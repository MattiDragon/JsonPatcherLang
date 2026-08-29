package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.tag.builtin.ConditionTagProcessor;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.SuppressingCommentDiagnosticFilter;
import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.v2.TypeChecker2;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.document.condition.DocConditionChecker;
import dev.mattidragon.jsonpatcher.server.document.feature.*;
import dev.mattidragon.jsonpatcher.server.event.DocumentEventBus;
import dev.mattidragon.jsonpatcher.server.event.EventHandlerKey;
import dev.mattidragon.jsonpatcher.server.event.GlobalEventBus;
import dev.mattidragon.jsonpatcher.server.event.context.DocumentEventContext;
import dev.mattidragon.jsonpatcher.server.event.document.DocumentDataChangedEvent;
import dev.mattidragon.jsonpatcher.server.event.workspace.DocHolderRebuildEvent;
import dev.mattidragon.jsonpatcher.server.event.workspace.WorkspaceConfigChangedEvent;
import dev.mattidragon.jsonpatcher.server.index.DocumentIndex;
import dev.mattidragon.jsonpatcher.server.index.typing.PreTypingPass;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import dev.mattidragon.jsonpatcher.server.workspace.config.WorkspaceConfigManager;
import dev.mattidragon.jsonpatcher.server.workspace.settings.SettingsManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DocumentState {
    // The URI that the client gave us
    private final String externalName;
    // A URI that has passed through java.net.URI#toASCIIString for compatibility with workspace
    private final String internalName;
    private final LanguageClient client;
    private final TextDocumentClientCapabilities clientCapabilities;
    private final DocumentEventBus eventBus;
    private final List<EventHandlerKey> eventHandlerKeys = new ArrayList<>();

    private final DefinitionFinder definitionFinder;
    private final AutoCompleteHelper autoCompleteHelper;
    private final InlayHintProvider inlayHintProvider;
    private final FormattingProvider formattingProvider;

    private final DocHolder docHolder;
    private final SettingsManager settingsManager;
    private final WorkspaceConfigManager workspaceConfigManager;

    private String lastContent = "";

    private CompletableFuture<DocumentData> data = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));

    public DocumentState(String name, LanguageClient client, TextDocumentClientCapabilities clientCapabilities, WorkspaceManager workspace, GlobalEventBus globalEventBus) {
        this.externalName = name;
        this.internalName = getInternalName(name);
        this.client = client;
        this.clientCapabilities = clientCapabilities;
        this.eventBus = new DocumentEventBus(globalEventBus, new DocumentEventContext(this));

        this.docHolder = workspace.getDocFileManager().getHolder();
        this.settingsManager = workspace.getSettingsManager();
        this.workspaceConfigManager = workspace.getWorkspaceConfigManager();

        this.definitionFinder = new DefinitionFinder(() -> data, workspace);
        this.autoCompleteHelper = new AutoCompleteHelper(docHolder, () -> data);
        this.inlayHintProvider = new InlayHintProvider(() -> data, () -> settingsManager.settings().inlayTypesEnabled());
        this.formattingProvider = new FormattingProvider(() -> data, settingsManager);

        eventHandlerKeys.add(globalEventBus.listenWorkspace(DocHolderRebuildEvent.class, (event, context) -> updateContent(lastContent)));
        eventHandlerKeys.add(globalEventBus.listenWorkspace(WorkspaceConfigChangedEvent.class, (event, context) -> {
            if (event.affectedUriPredicate().test(internalName)) {
                updateContent(lastContent);
            }
        }));
    }

    /**
     * Tries to convert a URI given by the language client to a standard form used by java paths.
     * Upon failure this method returns the original external name.
     */
    private String getInternalName(String externalName) {
        try {
            return Path.of(new URI(externalName)).toUri().toASCIIString();
        } catch (URISyntaxException e) {
            System.err.println("Failed to parse uri: " + e);
            return externalName;
        } catch (FileSystemNotFoundException | IllegalArgumentException e) {
            // ignore, we'll just not use files from unknown uris
            return externalName;
        }
    }

    public void updateContent(String content) {
        lastContent = content;
        data = CompletableFuture.supplyAsync(() -> {
            var diagnostics = new DiagnosticsBuilder();
            var treeMetadata = new TreeMetadata();

            var docParser = new DocCommentHandler(diagnostics, treeMetadata);
            var commentAttacher = new CommentAttacher();
            var diagnosticFilter = new SuppressingCommentDiagnosticFilter();

            var tokens = Lexer.lex(content, internalName, diagnostics, CommentHandler.allOf(docParser, commentAttacher, diagnosticFilter)).tokens();

            var tokenLookup = new TokenLookup(tokens);

            var parseResult = Parser.parse(tokens, diagnostics, treeMetadata);
            var program = parseResult.program();
            var patchMetadata = parseResult.metadata();
            var metadata = parseResult.metadata();

            commentAttacher.process(program, treeMetadata);

            var conditionChecker = new DocConditionChecker(
                    patchMetadata,
                    workspaceConfigManager.allowedLibraryGroups(internalName),
                    workspaceConfigManager.langVersion(internalName)
            );

            var globals = docHolder.getGlobals()
                    .entrySet()
                    .stream()
                    .filter(entry -> {
                        var docMetadata = entry.getValue().entry().sharedData().metadata();
                        if (docMetadata == null) return true;
                        var condition = docMetadata.get(entry.getValue().entry(), ConditionTagProcessor.CONDITION);
                        return condition.map(conditionChecker::matches).orElse(true);
                    })
                    .map(Map.Entry::getKey)
                    .toList();
            var variableAnalysis = VariableAnalyser.analyse(program, treeMetadata, diagnostics, globals);

            ConstantAnalyser.analyse(program, treeMetadata); // parts of type checking relies on this
            PreTypingPass.apply(program, treeMetadata, docHolder.getTypeConverter(), diagnostics);
            TypeChecker2.typeCheck(program, treeMetadata, docHolder, diagnostics);

            Util.EXECUTOR.submit(() -> sendDiagnostics(diagnostics.build(diagnosticFilter)));

            var lookups = Lookups.get(program, treeMetadata);
            var index = new DocumentIndex(internalName);
            index.index(program, metadata, treeMetadata, variableAnalysis, docHolder);

            return new DocumentData(new SourceFile(internalName, content), program, patchMetadata, treeMetadata, docParser.entries(), lookups, tokenLookup, index);
        }, Util.EXECUTOR);

        data.thenAcceptAsync(documentData -> eventBus.fire(new DocumentDataChangedEvent(documentData)), Util.EXECUTOR);
    }

    private void sendDiagnostics(Diagnostics diagnostics) {
        var lspDiagnostics = new ArrayList<Diagnostic>();

        for (var diagnostic : diagnostics.all()) {
            var pos = diagnostic.pos();
            if (pos == null) continue; // Potentially report file wide problems later
            var lspDiagnostic = new Diagnostic(spanToRange(pos), diagnostic.message());
            lspDiagnostic.setCode(diagnostic.id());
            switch (diagnostic.kind()) {
                case INTERNAL_ERROR, ERROR -> lspDiagnostic.setSeverity(DiagnosticSeverity.Error);
                case WARNING -> lspDiagnostic.setSeverity(DiagnosticSeverity.Warning);
                case UNUSED -> {
                    lspDiagnostic.setSeverity(DiagnosticSeverity.Warning);
                    lspDiagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
                }
                case DEPRECATION -> {
                    lspDiagnostic.setSeverity(DiagnosticSeverity.Warning);
                    lspDiagnostic.setTags(List.of(DiagnosticTag.Deprecated));
                }
            }
            lspDiagnostic.setSource("JsonPatcher");

            lspDiagnostics.add(lspDiagnostic);
        }

        client.publishDiagnostics(new PublishDiagnosticsParams(externalName, lspDiagnostics));
    }

    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return data.thenApplyAsync(documentData -> SemanticTokenizer.getTokens(documentData, docHolder, clientCapabilities.getSemanticTokens()), Util.EXECUTOR);
    }

    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> getDefinitions(Position position) {
        return definitionFinder.getDefinitions(position).thenApply(Either::forLeft);
    }

    public CompletableFuture<List<? extends Location>> getReferences(Position position) {
        return definitionFinder.getReferences(position);
    }

    public CompletableFuture<@Nullable Hover> getHover(Position position) {
        return definitionFinder.getHover(position);
    }

    public CompletableFuture<@Nullable List<? extends DocumentHighlight>> getHighlight(Position position) {
        return definitionFinder.getHighlight(position);
    }

    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> autoComplete(Position position) {
        return autoCompleteHelper.autoComplete(position).thenApply(Either::forRight);
    }

    public CompletableFuture<List<InlayHint>> getInlayHints(Range range) {
        return inlayHintProvider.getHints(range);
    }

    public CompletableFuture<@Nullable List<? extends TextEdit>> formatDocument(FormattingOptions options) {
        return formattingProvider.format(options);
    }

    public static Position posToPosition(SourcePos pos) {
        return new Position(pos.row() - 1, pos.column());
    }

    public static Range spanToRange(SourceSpan span) {
        // TODO: Check if this is correct (different from other cases)
        var pos1 = new Position(span.from().row() - 1, span.from().column() - 1);
        var pos2 = posToPosition(span.to());
        return new Range(pos1, pos2);
    }

    public static Location spanToLocation(SourceSpan span) {
        if (!Objects.equals(span.from().file().name(), span.to().file().name())) {
            throw new IllegalArgumentException("Cross file span can't be converted to location");
        }
        return new Location(span.from().file().name(), spanToRange(span));
    }

    public void close() {
        eventHandlerKeys.forEach(EventHandlerKey::removeHandler);
    }
}

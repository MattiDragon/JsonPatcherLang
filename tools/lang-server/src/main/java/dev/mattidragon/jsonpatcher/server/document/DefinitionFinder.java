package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeProperty;
import dev.mattidragon.jsonpatcher.docs.write.DocEntryWriter;
import dev.mattidragon.jsonpatcher.docs.write.DocWriter;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeFormatter;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.SpecialType;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.Type;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.index.IndexEntry;
import dev.mattidragon.jsonpatcher.server.index.StaticCombinedIndex;
import dev.mattidragon.jsonpatcher.server.index.symbol.*;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.eclipse.lsp4j.*;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DefinitionFinder {
    static final SourceFile LOOKUP_FAKE_FILE = new SourceFile("lookup fake file", "");

    private final Renderer renderer = MarkdownRenderer.builder().extensions(DocWriter.EXTENSIONS).build();

    private final Supplier<CompletableFuture<DocumentData>> documentData;
    private final WorkspaceManager workspace;

    public DefinitionFinder(Supplier<CompletableFuture<DocumentData>> documentData, WorkspaceManager workspace) {
        this.documentData = documentData;
        this.workspace = workspace;
    }

    public CompletableFuture<List<Location>> getDefinitions(Position position) {
        return documentData.get().thenApplyAsync(data -> {
            // TODO: nudge position to the left if we're right after a word token
            var pos = new SourcePos(data.sourceFile(), position.getLine() + 1, position.getCharacter() + 1);
            var combinedIndex = new StaticCombinedIndex(data.index(), workspace.getWorkspaceIndex());

            return combinedIndex.lookupEntries(pos)
                    .filter(Predicate.not(IndexEntry::isDeclaration))
                    .map(entry -> new IndexEntry(entry.symbol(), true))
                    .flatMap(combinedIndex::find)
                    .map(DocumentState::spanToLocation)
                    .toList();
        }, Util.EXECUTOR);
    }

    public CompletableFuture<List<? extends Location>> getReferences(Position position) {
        return documentData.get().thenApplyAsync(data -> {
            var pos = new SourcePos(data.sourceFile(), position.getLine() + 1, position.getCharacter() + 1);
            var combinedIndex = new StaticCombinedIndex(data.index(), workspace.getWorkspaceIndex());

            return combinedIndex.lookupEntries(pos)
                    .filter(IndexEntry::isDeclaration)
                    .map(entry -> new IndexEntry(entry.symbol(), false))
                    .flatMap(combinedIndex::find)
                    .map(DocumentState::spanToLocation)
                    .toList();
        }, Util.EXECUTOR);
    }

    public CompletableFuture<@Nullable Hover> getHover(Position position) {
        return documentData.get().thenApplyAsync(data -> {
            var pos = new SourcePos(data.sourceFile(), position.getLine() + 1, position.getCharacter() + 1);
            var combinedIndex = new StaticCombinedIndex(data.index(), workspace.getWorkspaceIndex());

            return combinedIndex.lookupEntries(pos)
                    .map(IndexEntry::symbol)
                    .map(symbol -> getSymbolDocs(symbol, data.treeMetadata()))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .map(renderer::render)
                    .map(markdown -> new MarkupContent(MarkupKind.MARKDOWN, markdown))
                    .map(Hover::new)
                    .orElse(null);
        });
    }

    private Optional<Document> getSymbolDocs(Symbol symbol, TreeMetadata metadata) {
        return switch (symbol) {
            case DocEntrySymbol(var namespace, var name) ->
                workspace.getDocManager()
                        .getHolder()
                        .getDocEntry(namespace, name)
                        .map(this::renderDocEntry);
            case LibrarySymbol(var location) ->
                workspace.getDocManager()
                        .getHolder()
                        .getLibrary(location)
                        .map(this::renderDocEntry);
            case GlobalSymbol(var name) ->
                workspace.getDocManager()
                        .getHolder()
                        .getGlobal(name)
                        .map(DocHolder.ObjectData::entry)
                        .map(this::renderDocEntry);
            case PropertySymbol(var namespace, var owner, var name) ->
                workspace.getDocManager()
                        .getHolder()
                        .getObject(namespace, owner)
                        .map(object -> object.properties().get(name))
                        .map(DocTreeProperty::entry)
                        .map(this::renderDocEntry);
            case MetadataSymbol(var name) ->
                    workspace.getDocManager()
                            .getHolder()
                            .getMetadataTag(name)
                            .map(this::renderDocEntry);

            case VariableSymbol(var variable) ->
                    Optional.of(getVariableDocs(variable, metadata));
            default -> Optional.empty();
        };
    }

    private Document renderDocEntry(DocEntry docEntry) {
        var document = new Document();
        DocEntryWriter.write(document, docEntry, 3);
        return document;
    }

    private Document getVariableDocs(Variable variable, TreeMetadata metadata) {
        var type = metadata.get(variable.definition(), TypeChecker.TYPE).orElse(SpecialType.UNKNOWN);

        var document = new Document();
        var code = new FencedCodeBlock();
        code.setInfo("jsonpatcher");
        var builder = new StringBuilder();
        switch (variable.definition()) {
            case FunctionDeclarationStatement statement -> {
                builder.append("function ");
                builder.append(variable.name());
                if (type != SpecialType.UNKNOWN) {
                    builder.append(": ");
                    writeType(type, builder);
                }
            }
            case ImportStatement(var libraryName, var variableName) -> {
                builder.append("import \"");
                builder.append(libraryName);
                builder.append("\" as ");
                builder.append(variableName);
            }
            case FunctionArgument(var target, var defaultValue) -> {
                builder.append("param ");
                builder.append(variable.name());
                if (defaultValue.isPresent()) {
                    builder.append("?");
                }
                if (type != SpecialType.UNKNOWN) {
                    builder.append(": ");
                    writeType(type, builder);
                }
            }
            default -> {
                builder.append(variable.mutable() ? "var " : "val ");
                builder.append(variable.name());
                if (type != SpecialType.UNKNOWN) {
                    builder.append(": ");
                    writeType(type, builder);
                }
            }
        }
        code.setLiteral(builder.toString());
        document.appendChild(code);
        return document;
    }

    public static void writeType(Type type, StringBuilder builder) {
        builder.append(TypeFormatter.format(type));
    }
}

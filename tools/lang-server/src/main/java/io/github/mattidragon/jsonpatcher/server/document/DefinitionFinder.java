package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.data.DocType;
import io.github.mattidragon.jsonpatcher.docs.write.DocWriter;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.PropertyAccessExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.VariableAccessExpression;
import io.github.mattidragon.jsonpatcher.server.Util;
import io.github.mattidragon.jsonpatcher.server.workspace.DocHolder;
import io.github.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.commonmark.node.Document;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.eclipse.lsp4j.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.mattidragon.jsonpatcher.server.document.DocumentState.spanToRange;

public class DefinitionFinder {
    private final Renderer renderer = MarkdownRenderer.builder().extensions(DocWriter.DEFAULT_EXTENSIONS).build();
    private final DocWriter docWriter = new DocWriter(List.of());
    
    private final Supplier<CompletableFuture<TreeAnalysis>> analysis;
    private final Supplier<CompletableFuture<List<DocEntry>>> docs;
    private final WorkspaceManager workspace;
    private final String documentUri;

    public DefinitionFinder(Supplier<CompletableFuture<TreeAnalysis>> analysis, Supplier<CompletableFuture<List<DocEntry>>> docs, WorkspaceManager workspace, String documentUri) {
        this.analysis = analysis;
        this.docs = docs;
        this.workspace = workspace;
        this.documentUri = documentUri;
        docWriter.setHeadingLevel(4);
        docWriter.setInlineDefinitions(true);
        docWriter.setValueSubHeaders(false);
    }

    public CompletableFuture<List<Location>> getDefinitions(Position position) {
        var pos = new SourcePos(null, position.getLine() + 1, position.getCharacter() + 1);
        return analysis.get().thenCombineAsync(docs.get(), (analysis, docs) -> {
            var list = new ArrayList<Location>();
            addVariableDefinitions(analysis, pos, list);
            addImportLocationDefinitions(analysis, pos, list);
            addDocDefinitions(docs, pos, list);
            return list;
        }, Util.EXECUTOR);
    }

    private void addDocDefinitions(List<DocEntry> docs, SourcePos pos, ArrayList<Location> list) {
        forDocRefsAt(docs, pos, doc -> {
            var span = doc.entry().namePos();
            if (span == null) return;
            list.add(new Location(doc.file().uri(), spanToRange(span)));
        });
    }
    
    private void addImportLocationDefinitions(TreeAnalysis analysis, SourcePos pos, ArrayList<Location> list) {
        analysis.getImportedModules()
                .getAllAt(pos)
                .map(workspace.getDocManager().getHolder()::getModuleData)
                .flatMap(Optional::stream)
                .map(DocHolder.ModuleData::entry)
                .flatMap(module -> Optional.ofNullable(module.locationPos())
                        .or(() -> Optional.ofNullable(module.namePos()))
                        .stream())
                .map(DocumentState::spanToLocation)
                .forEach(list::add);
    }

    private void addVariableDefinitions(TreeAnalysis analysis, SourcePos pos, List<Location> list) {
        analysis.getVariableReferences()
                .getAllAt(pos)
                .<Location>mapMulti((variable, consumer) -> {
                    if (variable.stdlib()) {
                        workspace.getDocManager()
                                .getHolder()
                                .getStdlibModule(variable.name())
                                .map(DocHolder.ModuleData::entry)
                                .map(DocEntry.Module::namePos)
                                .map(DocumentState::spanToLocation)
                                .ifPresent(consumer);
                    } else {
                        Optional.ofNullable(variable.definitionPos())
                                .map(DocumentState::spanToLocation)
                                .ifPresent(consumer);
                    }
                })
                .forEach(list::add);
    }

    public CompletableFuture<List<? extends Location>> getReferences(Position position) {
        var pos = new SourcePos(null, position.getLine() + 1, position.getCharacter() + 1);
        return analysis.get().thenApplyAsync(analysis -> {
            var variableReferences = analysis.getVariableReferences();
            return variableReferences
                    .getAllAt(pos)
                    .map(variableReferences::getPositions)
                    .flatMap(List::stream)
                    .filter(span -> span.from().row() > pos.row() || span.to().row() < pos.row() || span.from().column() > pos.column() || span.to().column() < pos.column())
                    .map(DocumentState::spanToRange)
                    .map(range -> new Location(documentUri, range))
                    .toList();
        }, Util.EXECUTOR);
    }

    public CompletableFuture<Hover> getHover(Position position) {
        var pos = new SourcePos(null, position.getLine() + 1, position.getCharacter() + 1);
        return analysis.get().thenCombineAsync(docs.get(), (analysis, localDocs) ->
                getVariableDocs(analysis, pos)
                        .or(() -> getPropertyDocs(analysis, pos))
                        .or(() -> getNestedDocs(localDocs, pos))
                        .map(entry -> {
                            var document = new Document();
                            docWriter.writeEntry(document, entry);
                            return document;
                        })
                        .map(renderer::render)
                        .map(markdown -> new MarkupContent(MarkupKind.MARKDOWN, markdown))
                        .map(Hover::new)
                        .orElse(null));
    }

    private Optional<DocEntry> getNestedDocs(List<DocEntry> entries, SourcePos pos) {
        var out = new ArrayList<DocEntry>();
        forDocRefsAt(entries, pos, doc -> out.add(doc.entry()));
        return out.isEmpty() ? Optional.empty() : Optional.of(out.getFirst());
    }

    private Optional<DocEntry> getVariableDocs(TreeAnalysis analysis, SourcePos pos) {
        return Optional.ofNullable(analysis.getVariableReferences().getFirstAt(pos))
                .flatMap(this::getDocs)
                .map(DocHolder.OwnerData::entry);
    }

    private Optional<DocEntry> getPropertyDocs(TreeAnalysis analysis, SourcePos pos) {
        var access = analysis.getPropertyAccesses().getFirstAt(pos);
        if (!(access instanceof PropertyAccessExpression(VariableAccessExpression variableAccess, var name, var pos1, var pos2))) {
            return Optional.empty();
        }

        return Optional.ofNullable(analysis.getVariableDefinition(variableAccess))
                .flatMap(this::getDocs)
                .map(DocHolder.OwnerData::values)
                .map(valueMap -> valueMap.get(name));
    }

    private Optional<DocHolder.OwnerData> getDocs(TreeAnalysis.VariableDefinition variable) {
        var docHolder = workspace.getDocManager().getHolder();
        if (variable.stdlib()) {
            return docHolder.getStdlibModule(variable.name()).map(Function.identity());
        }
        if (variable instanceof TreeAnalysis.ImportDefinition importDefinition) {
            return Optional.ofNullable(importDefinition.statement())
                    .flatMap(statement -> docHolder.getModuleData(statement.libraryName()));
        }
        return Optional.empty();
    }

    private void forDocRefsAt(List<DocEntry> docs, SourcePos pos, Consumer<DocHolder.OwnerData> consumer) {
        var docHolder = workspace.getDocManager().getHolder();
        for (var doc : docs) {
            DocType definition = null;
            if (doc instanceof DocEntry.Type type) {
                definition = type.definition();
            } else if (doc instanceof DocEntry.Value value) {
                definition = value.definition();

                if (value.ownerPos() != null && value.ownerPos().contains(pos)) {
                    docHolder.getOwnerData(value.owner()).ifPresent(consumer);
                }
            }

            if (definition != null) {
                walkDocTypes(definition, docType -> {
                    if (!(docType instanceof DocType.Name nameType)) return;
                    if (nameType.pos() == null || !nameType.pos().contains(pos)) return;
                    docHolder.getTypeData(nameType.name())
                            .ifPresent(consumer);
                });
            }
        }
    }
    
    private void walkDocTypes(DocType type, Consumer<DocType> visitor) {
        switch (type) {
            case DocType.Array array -> {
                visitor.accept(array);
                walkDocTypes(array.entry(), visitor);
            }
            case DocType.Object object -> {
                visitor.accept(object);
                walkDocTypes(object.entry(), visitor);
            }
            case DocType.Function function -> {
                visitor.accept(function);
                for (var arg : function.args()) {
                    walkDocTypes(arg.type(), visitor);
                }
                walkDocTypes(function.returnType(), visitor);
            }
            case DocType.Union union -> {
                visitor.accept(union);
                for (var child : union.children()) {
                    walkDocTypes(child, visitor);
                }
            }
            case DocType.Name name -> visitor.accept(name);
            case DocType.Special special -> visitor.accept(special);
        }
    }
}

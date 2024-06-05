package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.data.DocType;
import io.github.mattidragon.jsonpatcher.docs.write.DocWriter;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.VariableAccessExpression;
import io.github.mattidragon.jsonpatcher.server.Util;
import io.github.mattidragon.jsonpatcher.server.workspace.DocTree;
import io.github.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.commonmark.node.Document;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.eclipse.lsp4j.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.github.mattidragon.jsonpatcher.server.document.DocumentState.spanToRange;

public class DefinitionFinder {
    private final Renderer renderer = MarkdownRenderer.builder().extensions(DocWriter.DEFAULT_EXTENSIONS).build();
    private final DocWriter emptyDocWriter = new DocWriter(List.of());
    private final Supplier<CompletableFuture<TreeAnalysis>> analysis;
    private final Supplier<CompletableFuture<List<DocEntry>>> docs;
    private final WorkspaceManager workspace;
    private final String documentUri;

    public DefinitionFinder(Supplier<CompletableFuture<TreeAnalysis>> analysis, Supplier<CompletableFuture<List<DocEntry>>> docs, WorkspaceManager workspace, String documentUri) {
        this.analysis = analysis;
        this.docs = docs;
        this.workspace = workspace;
        this.documentUri = documentUri;
        emptyDocWriter.setHeadingLevel(4);
        emptyDocWriter.setInlineDefinitions(true);
        emptyDocWriter.setValueSubHeaders(false);
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
            list.add(new Location(doc.owner().uri(), spanToRange(span)));
        });
    }
    
    private void addImportLocationDefinitions(TreeAnalysis analysis, SourcePos pos, ArrayList<Location> list) {
        analysis.getImportedModules()
                .getAllAt(pos)
                .map(workspace.getDocManager().getDocTree()::getModuleData)
                .filter(Objects::nonNull)
                .map(moduleDoc -> {
                    var module = moduleDoc.entry();
                    var modulePos = module.locationPos();
                    if (modulePos == null) modulePos = module.namePos();
                    if (modulePos == null) return null;
                    return new Location(moduleDoc.owner().uri(), spanToRange(modulePos));
                })
                .filter(Objects::nonNull)
                .forEach(list::add);
    }

    private void addVariableDefinitions(TreeAnalysis analysis, SourcePos pos, List<Location> list) {
        analysis.getVariableReferences()
                .getAllAt(pos)
                .<Location>mapMulti((variable, consumer) -> {
                    if (variable.stdlib()) {
                        var moduleDoc = workspace.getDocManager().getDocTree().getStdlibModule(variable.name());
                        if (moduleDoc == null) return;
                        var span = moduleDoc.entry().namePos();
                        if (span == null) return;
                        consumer.accept(new Location(moduleDoc.owner().uri(), spanToRange(span)));
                    } else {
                        var span = variable.definitionPos();
                        if (span == null) return;
                        consumer.accept(new Location(documentUri, spanToRange(span)));
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
        return analysis.get().thenCombineAsync(docs.get(), (analysis, localDocs) -> {
            var docs = getVariableDocs(analysis, pos);
            if (docs == null) docs = getPropertyDocs(analysis, pos);
            if (docs == null) docs = getNestedDocs(localDocs, pos);
            if (docs == null) return null;
            var document = new Document();
            emptyDocWriter.writeEntry(document, docs);
            var markdown = renderer.render(document);
            return new Hover(new MarkupContent(MarkupKind.MARKDOWN, markdown));
        });
    }

    private @Nullable DocEntry getNestedDocs(List<DocEntry> entries, SourcePos pos) {
        var out = new ArrayList<DocEntry>();
        forDocRefsAt(entries, pos, doc -> out.add(doc.entry()));
        return out.isEmpty() ? null : out.getFirst();
    }

    private @Nullable DocEntry getVariableDocs(TreeAnalysis analysis, SourcePos pos) {
        var ref = analysis.getVariableReferences().getFirstAt(pos);
        if (ref == null) return null;
        var docs = getDocs(ref, analysis);
        if (docs == null) return null;
        return docs.entry();
    }

    private @Nullable DocEntry getPropertyDocs(TreeAnalysis analysis, SourcePos pos) {
        var access = analysis.getPropertyAccesses().getFirstAt(pos);
        if (access == null) return null;
        if (!(access.parent() instanceof VariableAccessExpression variableAccess)) return null;
        var moduleVariable = analysis.getVariableDefinition(variableAccess);
        if (moduleVariable == null) return null;
        var moduleDoc = getDocs(moduleVariable, analysis);
        if (moduleDoc == null) return null;
        return moduleDoc.values().get(access.name());
    }

    @Nullable
    private DocTree.OwnerDoc getDocs(TreeAnalysis.Variable variable, TreeAnalysis analysis) {
        var docTree = workspace.getDocManager().getDocTree();
        if (variable.stdlib()) {
            return docTree.getStdlibModule(variable.name());
        }
        if (variable.kind() == TreeAnalysis.Variable.Kind.IMPORT) {
            var statement = analysis.getImportStatement(variable);
            if (statement == null) return null;
            return docTree.getModuleData(statement.libraryName());
        }
        return null;
    }

    private void forDocRefsAt(List<DocEntry> docs, SourcePos pos, Consumer<DocTree.OwnerDoc> consumer) {
        var docTree = workspace.getDocManager().getDocTree();
        for (var doc : docs) {
            DocType definition = null;
            if (doc instanceof DocEntry.Type type) {
                definition = type.definition();
            } else if (doc instanceof DocEntry.Value value) {
                definition = value.definition();

                if (value.ownerPos() != null && value.ownerPos().contains(pos)) {
                    var owner = docTree.getOwner(value.owner());
                    if (owner != null) {
                        consumer.accept(owner);
                    }
                }
            }

            if (definition != null) {
                walkDocTypes(definition, docType -> {
                    if (!(docType instanceof DocType.Name nameType)) return;
                    if (nameType.pos() == null || !nameType.pos().contains(pos)) return;
                    var data = docTree.getTypeData(nameType.name());
                    if (data == null) return;
                    consumer.accept(data);
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

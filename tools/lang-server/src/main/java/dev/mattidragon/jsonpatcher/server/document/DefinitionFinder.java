package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.data.DocType;
import dev.mattidragon.jsonpatcher.docs.write.DocWriter;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.commonmark.node.Document;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DefinitionFinder {
    static final SourceFile LOOKUP_FAKE_FILE = new SourceFile("lookup fake file", "");

    private final Renderer renderer = MarkdownRenderer.builder().extensions(DocWriter.DEFAULT_EXTENSIONS).build();
    private final DocWriter docWriter = new DocWriter(List.of());

    private final Supplier<CompletableFuture<DocumentData>> documentData;
    private final WorkspaceManager workspace;
    private final String documentUri;

    public DefinitionFinder(Supplier<CompletableFuture<DocumentData>> documentData, WorkspaceManager workspace, String documentUri) {
        this.documentData = documentData;
        this.workspace = workspace;
        this.documentUri = documentUri;
        docWriter.setHeadingLevel(4);
        docWriter.setInlineDefinitions(true);
        docWriter.setValueSubHeaders(false);
    }

    public CompletableFuture<List<Location>> getDefinitions(Position position) {
        var pos = new SourcePos(LOOKUP_FAKE_FILE, position.getLine() + 1, position.getCharacter() + 1);
        return documentData.get().thenApplyAsync(data -> {
            var list = new ArrayList<Location>();
            addVariableDefinitions(data.lookups(), pos, list);
//            addImportLocationDefinitions(data.lookups(), pos, list);
//            addDocDefinitions(data.docs(), pos, list);
            return list;
        }, Util.EXECUTOR);
    }

//    private void addDocDefinitions(List<DocEntry> docs, SourcePos pos, ArrayList<Location> list) {
//        forDocRefsAt(docs, pos, doc -> {
//            var span = doc.entry().namePos();
//            if (span == null) return;
//            list.add(new Location(doc.file().uri(), spanToRange(span)));
//        });
//    }
//
//    private void addImportLocationDefinitions(Lookups lookups, SourcePos pos, ArrayList<Location> list) {
//        lookups.libraryImports()
//                .getAllAt(pos)
//                .map(workspace.getDocManager().getHolder()::getModuleData)
//                .flatMap(Optional::stream)
//                .map(DocHolder.ModuleData::entry)
//                .flatMap(module -> Optional.ofNullable(module.locationPos())
//                        .or(() -> Optional.ofNullable(module.namePos()))
//                        .stream())
//                .map(DocumentState::spanToLocation)
//                .forEach(list::add);
//    }

    private void addVariableDefinitions(Lookups lookups, SourcePos pos, List<Location> list) {
        var metadata = lookups.treeMetadata();
        lookups.variableReferences()
                .getAllAt(pos)
                .<Location>mapMulti((variable, consumer) -> {
                    if (variable.definition() instanceof Program) {
                        workspace.getDocManager()
                                .getHolder()
                                .getGlobal(variable.name())
                                .map(DocHolder.ObjectData::entry)
                                .flatMap(entry -> metadata.get(entry, MetadataKey.MAIN_POS))
                                .map(DocumentState::spanToLocation)
                                .ifPresent(consumer);
                    } else {
                        metadata.get(variable.definition(), MetadataKey.NAME_POS)
                                .map(DocumentState::spanToLocation)
                                .ifPresent(consumer);
                    }
                })
                .forEach(list::add);
    }

    public CompletableFuture<List<? extends Location>> getReferences(Position position) {
        var pos = new SourcePos(LOOKUP_FAKE_FILE, position.getLine() + 1, position.getCharacter() + 1);
        return documentData.get().thenApplyAsync(data -> {
            var lookups = data.lookups();
            var variableReferences = lookups.variableReferences();
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

//    public CompletableFuture<Hover> getHover(Position position) {
//        var pos = new SourcePos(LOOKUP_FAKE_FILE, position.getLine() + 1, position.getCharacter() + 1);
//        return documentData.get().thenApplyAsync(data -> {
//            var lookups = data.lookups();
//            return getVariableDocs(lookups, pos)
//                    .or(() -> getPropertyDocs(lookups, pos))
//                    .or(() -> getNestedDocs(data.docs(), pos))
//                    .map(entry -> {
//                        var document = new Document();
//                        docWriter.writeEntry(document, entry);
//                        return document;
//                    })
//                    .or(() -> getLocalInfo(lookups, pos))
//                    .map(renderer::render)
//                    .map(markdown -> new MarkupContent(MarkupKind.MARKDOWN, markdown))
//                    .map(Hover::new)
//                    .orElse(null);
//        });
//    }
//
//    private Optional<DocEntry> getNestedDocs(List<DocEntry> entries, SourcePos pos) {
//        var out = new ArrayList<DocEntry>();
//        forDocRefsAt(entries, pos, doc -> out.add(doc.entry()));
//        return out.isEmpty() ? Optional.empty() : Optional.of(out.getFirst());
//    }

    private Optional<Document> getLocalInfo(Lookups lookups, SourcePos pos) {
        return Optional.ofNullable(lookups.variableReferences().getFirstAt(pos))
                .map(variable -> {
                    var document = new Document();
                    var code = new FencedCodeBlock();
                    code.setInfo("jsonpatcher");
                    var builder = new StringBuilder();
                    switch (variable.definition()) {
                        case FunctionDeclarationStatement statement -> {
                            builder.append("function ");
                            builder.append(variable.name());
                            appendFunctionDesc(statement, builder);
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
                        }
                        default -> {
                            builder.append(variable.mutable() ? "var " : "val ");
                            builder.append(variable.name());
                        }
                    }
                    code.setLiteral(builder.toString());
                    document.appendChild(code);
                    return document;
                });
    }

    private static void appendFunctionDesc(FunctionDeclarationStatement statement, StringBuilder builder) {
        if (statement != null) {
            builder.append("(");
            var args = statement.value().args();
            var first = true;
            for (var arg : args.arguments()) {
                if (first) {
                    first = false;
                } else {
                    builder.append(", ");
                }
                builder.append(switch (arg.target()) {
                    case FunctionArgument.Target.Root root -> "$";
                    case FunctionArgument.Target.Variable variable -> variable.name();
                });
                if (arg.defaultValue().isPresent()) {
                    builder.append("?");
                }
            }
            if (args.varargs()) {
                builder.append("*");
            }
            builder.append(")");
        }
    }

//    private Optional<DocEntry> getVariableDocs(Lookups lookups, SourcePos pos) {
//        return Optional.ofNullable(lookups.variableReferences().getFirstAt(pos))
//                .flatMap(this::getDocs)
//                .map(DocHolder.DocsData::entry);
//    }
//
//    private Optional<DocEntry> getPropertyDocs(Lookups lookups, SourcePos pos) {
//        var access = lookups.propertyAccesses().getFirstAt(pos);
//        if (!(access instanceof PropertyAccessExpression(VariableAccessExpression variableAccess, var name))) {
//            return Optional.empty();
//        }
//
//        var variable = lookups.treeMetadata().get(variableAccess, VariableAnalyser.VARIABLE_REFERENCE);
//
//        return variable.flatMap(this::getDocs)
//                .flatMap(data ->
//                        data instanceof DocHolder.OwnerData ownerData ? Optional.of(ownerData.values()) : Optional.empty())
//                .map(valueMap -> valueMap.get(name));
//    }

//    private Optional<DocHolder.DocsData> getDocs(Variable variable) {
//        var docHolder = workspace.getDocManager().getHolder();
//        if (variable.stdlib()) {
//            return docHolder.getGlobal(variable.name()).map(Function.identity());
//        }
//        if (variable.definition() instanceof ImportStatement importStatement) {
//            return docHolder.getModuleData(importStatement.libraryName()).map(Function.identity());
//        }
//        return Optional.empty();
//    }
//
//    private void forDocRefsAt(List<DocEntry> docs, SourcePos pos, Consumer<DocHolder.OwnerData> consumer) {
//        var docHolder = workspace.getDocManager().getHolder();
//        for (var doc : docs) {
//            DocType definition = null;
//            if (doc instanceof DocEntry.Type type) {
//                definition = type.definition();
//            } else if (doc instanceof DocEntry.Value value) {
//                definition = value.definition();
//
//                if (value.ownerPos() != null && value.ownerPos().contains(pos)) {
//                    docHolder.getOwnerData(value.owner()).ifPresent(consumer);
//                }
//            }
//
//            if (definition != null) {
//                walkDocTypes(definition, docType -> {
//                    if (!(docType instanceof DocType.Name(var name, var nameSpan))) return;
//                    if (nameSpan == null || !nameSpan.contains(pos)) return;
//                    docHolder.getTypeData(name)
//                            .ifPresent(consumer);
//                });
//            }
//        }
//    }
    
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

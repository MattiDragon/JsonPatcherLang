package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.PrimitiveProperties;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeComparison;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Scope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import org.eclipse.lsp4j.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static dev.mattidragon.jsonpatcher.server.document.DefinitionFinder.LOOKUP_FAKE_FILE;

public class AutoCompleteHelper {
    private final DocHolder docs;
    private final Supplier<CompletableFuture<DocumentData>> dataGetter;

    public AutoCompleteHelper(DocHolder docs, Supplier<CompletableFuture<DocumentData>> dataGetter) {
        this.docs = docs;
        this.dataGetter = dataGetter;
    }

    public CompletableFuture<CompletionList> autoComplete(Position position) {
        var pos = new SourcePos(LOOKUP_FAKE_FILE, position.getLine() + 1, position.getCharacter());
        return dataGetter.get().thenApplyAsync(data -> {
            var currentToken = data.tokens().getAt(pos);
            var prevToken = currentToken == null ? null : currentToken.previous();
            var completions = new ArrayList<CompletionItem>();

            Token selectorToken;
            if (currentToken != null && !(currentToken.token() instanceof Token.WordToken)) {
                selectorToken = currentToken.token();
            } else if (prevToken != null) {
                selectorToken = prevToken.token();
            } else {
                selectorToken = null;
            }

            if (selectorToken == Token.SimpleToken.DOT || selectorToken == Token.SimpleToken.DOLLAR) {
                completeProperty(data, currentToken, completions);
            } else if (selectorToken == Token.SimpleToken.AT_SIGN) {
                completeMetadata(completions);
            } else {
                completeVariable(data, pos, completions);
                completeConstant(completions);
            }

            return new CompletionList(completions);
        }, Util.EXECUTOR);
    }

    private void completeMetadata(ArrayList<CompletionItem> completions) {
        docs.getMetadataTags()
                .values()
                .stream()
                .map(this::buildMetadataCompletion)
                .forEach(completions::add);
    }

    private CompletionItem buildMetadataCompletion(DocEntry.MetadataEntry metadataEntry) {
        var completion = new CompletionItem(metadataEntry.name());
        completion.setKind(CompletionItemKind.Property);

        var type = docs.getTypeConverter().convert(metadataEntry.type());

        completion.setInsertTextFormat(InsertTextFormat.Snippet);
        completion.setInsertText(metadataEntry.name() + getValueTemplate(type) + ";");

        return completion;
    }

    private String getValueTemplate(Type type) {
        return switch (type) {
            case PrimitiveType.STRING -> " \"$1\"";
            case PrimitiveType.ARRAY -> " [$1]";
            case ArrayType arrayType -> " [$1]";
            case PrimitiveType.OBJECT -> " {$1}";
            case ObjectType objectType -> " {$1}";
            case PrimitiveType.NULL -> "";
            case PrimitiveType primitiveType -> " $1";
            case SpecialType specialType -> " $1";
            case LazyType lazyType -> getValueTemplate(lazyType.get());
            case NamedType namedType -> {
                var s = new StringBuilder(" {");
                var i = 1;
                for (var key : namedType.properties().keySet()) {
                    if (i != 1) s.append(",");
                    s.append("\n  \"").append(key).append("\": $").append(i++);
                }
                s.append("\n}");
                yield s.toString();
            }
            case UnionType unionType -> TypeComparison.isSubtype(PrimitiveType.NULL, unionType) ? "" : " $1";
            // Should never be used for metadata
            case TypeArgument typeArgument -> "";
            case FunctionType functionType -> "";
        };
    }

    private void completeProperty(DocumentData data, TokenLookup.IndexedToken currentToken, ArrayList<CompletionItem> completions) {
        data.lookups()
                .propertyAccesses()
                .getAllAt(currentToken.pos().from())
                .reduce((a, b) -> b)
                .flatMap(expression -> data.treeMetadata().get(expression.parent(), TypeChecker.TYPE))
                .map(this::getTypeProperties)
                .stream()
                .flatMap(Collection::stream)
                .map(this::buildPropertyCompletion)
                .forEach(completions::add);
    }

    private CompletionItem buildPropertyCompletion(PropertyCompletionInfo info) {
        var completion = new CompletionItem(info.name);

        var isFunction = info.propertyType instanceof FunctionType || info.propertyType == PrimitiveType.FUNCTION;
        var isOwned = true;
        if (info.ownerType instanceof NamedType namedType) {
            var docEntry = docs.getDocEntry(namedType.name());
            if (docEntry.isPresent() &&
                (docEntry.get() instanceof DocEntry.LibraryEntry || docEntry.get() instanceof DocEntry.GlobalLibraryEntry)) {
                isOwned = false;
            }
        }

        if (isFunction && isOwned) {
            completion.setKind(CompletionItemKind.Method);
        } else if (isFunction) {
            completion.setKind(CompletionItemKind.Function);
        } else if (isOwned) {
            completion.setKind(CompletionItemKind.Field);
        } else {
            completion.setKind(CompletionItemKind.Value);
        }

        if (isFunction) {
            var argCount = info.propertyType instanceof FunctionType functionType ? functionType.requiredArgs() : 1;
            completion.setInsertTextFormat(InsertTextFormat.Snippet);
            var snippet = new StringBuilder(info.name);
            snippet.append("(");
            for (var i = 0; i < argCount; i++) {
                if (i != 0) {
                    snippet.append(", ");
                }
                snippet.append("$").append(i + 1);
            }
            snippet.append(")");
            completion.setInsertText(snippet.toString());
        }

        return completion;
    }

    private Collection<PropertyCompletionInfo> getTypeProperties(Type type) {
        return switch (type) {
            case LazyType lazyType -> getTypeProperties(lazyType.get());
            case NamedType namedType -> namedType.properties()
                    .entrySet()
                    .stream()
                    .map(entry -> new PropertyCompletionInfo(entry.getKey(), type, entry.getValue()))
                    .collect(Collectors.toSet());
            // This is unlikely to ever actually come up, but this should work
            case TypeArgument typeArgument -> getTypeProperties(typeArgument.bound());
            // Technically this should be an intersection, not a union of properties,
            // but users will often know better than us
            case UnionType unionType -> unionType.children()
                    .stream()
                    .map(this::getTypeProperties)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            default -> docs.getPrimitivePropertyTypes().get(PrimitiveProperties.convertType(type))
                    .entrySet()
                    .stream()
                    .map(entry -> new PropertyCompletionInfo(entry.getKey(), type, entry.getValue()))
                    .collect(Collectors.toSet());
        };
    }

    private void completeVariable(DocumentData data, SourcePos pos, ArrayList<CompletionItem> completions) {
        var metadata = data.treeMetadata();

        var variables = findVariables(data, pos, metadata);

        for (var variable : variables) {
            var completion = new CompletionItem(variable.name());
            // TODO: docs on imports and globals
            completion.setKind(CompletionItemKind.Variable);
            completions.add(completion);
        }
    }

    private static Set<Variable> findVariables(DocumentData data, SourcePos pos, TreeMetadata metadata) {
        var ignoredParents = new HashSet<Scope>();
        var scopes = data.lookups()
                .scopes()
                .getAllAt(pos)
                .collect(Collectors.toCollection(ArrayList::new));
        scopes.forEach(scope -> {
            var parent = scope.parent();
            while (parent != null) {
                ignoredParents.add(parent);
                parent = parent.parent();
            }
        });
        scopes.removeIf(ignoredParents::contains);

        var toFilter = new HashSet<Variable>();
        var notToFilter = new HashSet<Variable>();

        for (var scope : scopes) {
            var filtering = true;
            var checkScope = scope;
            while (checkScope != null) {
                if (filtering) {
                    toFilter.addAll(checkScope.variables());
                } else {
                    notToFilter.addAll(checkScope.variables());
                }

                if (scope instanceof FunctionScope) filtering = false;
                checkScope = checkScope.parent();
            }
        }

        toFilter.removeIf(variable -> {
            var defPos = metadata.get(variable.definition(), MetadataKey.FULL_POS);
            if (defPos.isEmpty()) return false; // Better have a few extras
            var defEndPos = defPos.get().to();

            return defEndPos.row() > pos.row() || (defEndPos.row() == pos.row() && defEndPos.column() > pos.column());
        });
        notToFilter.addAll(toFilter);
        return notToFilter;
    }

    private void completeConstant(ArrayList<CompletionItem> completions) {
        var nullItem = new CompletionItem("null");
        nullItem.setKind(CompletionItemKind.Constant);
        var falseItem = new CompletionItem("false");
        falseItem.setKind(CompletionItemKind.Constant);
        var trueItem = new CompletionItem("true");
        trueItem.setKind(CompletionItemKind.Constant);

        completions.addAll(Arrays.asList(nullItem, falseItem, trueItem));
    }

    private record PropertyCompletionInfo(String name, Type ownerType, Type propertyType) {
    }
}

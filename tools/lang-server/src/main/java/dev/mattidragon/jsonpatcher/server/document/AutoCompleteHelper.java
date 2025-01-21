package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Scope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
        completions.add(new CompletionItem("version"));
    }

    private void completeProperty(DocumentData data, TokenLookup.IndexedToken currentToken, ArrayList<CompletionItem> completions) {

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
}

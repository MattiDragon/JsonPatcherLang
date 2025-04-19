package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.NamedType;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.PropertyAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.server.index.symbol.*;

import java.util.Arrays;

public abstract class AstIndex extends LookupIndex {
    protected void indexVariables(VariableAnalysis analysis, TreeMetadata metadata) {
        for (var scope : analysis.scopes()) {
            for (var variable : scope.variables()) {
                Symbol symbol;
                if (variable.definition() instanceof Program) {
                    symbol = new GlobalSymbol(variable.name());
                } else {
                    symbol = new VariableSymbol(variable);
                }

                var definitionEntry = new IndexEntry(symbol, true);
                var usageEntry = new IndexEntry(symbol, false);

                metadata.get(variable.definition(), MetadataKey.NAME_POS)
                        .ifPresent(pos -> lookup.add(pos, definitionEntry));

                for (var usageNode : variable.usages()) {
                    metadata.get(usageNode, MetadataKey.NAME_POS)
                            .ifPresent(pos -> lookup.add(pos, usageEntry));
                }
            }
        }
    }

    protected void indexTree(ProgramNode node, TreeMetadata metadata) {
        switch (node) {
            case PropertyAccessExpression(var parent, var name) -> {
                var parentType = metadata.get(parent, TypeChecker.TYPE).orElse(null);
                if (!(parentType instanceof NamedType namedType)) break;
                if (!namedType.properties().containsKey(name)) break;

                var symbol = makePropertySymbol(namedType.name(), name);
                var entry = new IndexEntry(symbol, false);

                metadata.get(node, MetadataKey.NAME_POS)
                        .ifPresent(pos -> lookup.add(pos, entry));
            }
            case ImportStatement(var libraryName, var variableName) -> {
                var entry = new IndexEntry(new LibrarySymbol(libraryName), false);
                metadata.get(node, MetadataKey.IMPORT_LOCATION_POS)
                        .ifPresent(pos -> lookup.add(pos, entry));
            }
            default -> {}
        }
        for (var child : node.getChildren()) {
            indexTree(child, metadata);
        }
    }

    private static PropertySymbol makePropertySymbol(String typeName, String propertyName) {
        var dotIndex = typeName.lastIndexOf('.');
        if (dotIndex == -1) {
            return new PropertySymbol(NamespaceDescription.EMPTY, typeName, propertyName);
        }

        var namespace = typeName.substring(0, dotIndex);
        var owner = typeName.substring(dotIndex + 1);

        return new PropertySymbol(new NamespaceDescription(Arrays.asList(namespace.split("\\."))), owner, propertyName);
    }
}

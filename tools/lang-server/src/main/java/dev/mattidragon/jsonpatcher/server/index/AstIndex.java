package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.PrimitiveProperties;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.NamedType;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.PropertyAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.PatchMetadata;
import dev.mattidragon.jsonpatcher.server.index.symbol.*;
import dev.mattidragon.jsonpatcher.server.workspace.PrimitivePropertyAccess;

import java.util.Arrays;
import java.util.Optional;

abstract class AstIndex extends LookupIndex {
    AstIndex(String fileName) {
        super(fileName);
    }

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

    protected void indexMetadata(PatchMetadata metadata, TreeMetadata treeMetadata) {
        for (var entry : metadata.getAll().entrySet()) {
            var indexEntry = new IndexEntry(new MetadataSymbol(entry.getKey()), false);
            treeMetadata.get(entry.getValue(), MetadataKey.NAME_POS)
                    .ifPresent(pos -> lookup.add(pos, indexEntry));
        }
    }

    protected void indexTree(ProgramNode node, TreeMetadata metadata, PrimitivePropertyAccess docAccess) {
        switch (node) {
            case PropertyAccessExpression(var parent, var name) -> {
                var parentType = metadata.get(parent, TypeChecker.TYPE).orElse(null);
                if (parentType instanceof NamedType namedType) {
                    if (!namedType.properties().containsKey(name)) break;

                    var symbol = makePropertySymbol(namedType.name(), name);
                    var entry = new IndexEntry(symbol, false);

                    metadata.get(node, MetadataKey.NAME_POS)
                            .ifPresent(pos -> lookup.add(pos, entry));
                } else if (parentType != null) {
                    Optional.ofNullable(PrimitiveProperties.convertType(parentType))
                            .flatMap(t -> docAccess.getPrimitivePropertyDocs(t, name))
                            .ifPresent(property -> {
                                var symbol = new PropertySymbol(property.namespace(), property.owner(), property.name());
                                var entry = new IndexEntry(symbol, false);

                                metadata.get(node, MetadataKey.NAME_POS)
                                        .ifPresent(pos -> lookup.add(pos, entry));
                            });
                }
            }
            case ImportStatement(var libraryName, var variableName) -> {
                var entry = new IndexEntry(new LibrarySymbol(libraryName), false);
                metadata.get(node, MetadataKey.IMPORT_LOCATION_POS)
                        .ifPresent(pos -> lookup.add(pos, entry));
            }
            default -> {}
        }
        for (var child : node.getChildren()) {
            indexTree(child, metadata, docAccess);
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

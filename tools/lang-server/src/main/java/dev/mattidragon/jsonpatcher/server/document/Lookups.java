package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.Scope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.PropertyAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ForEachLoopStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;

public class Lookups {
    private final PosLookup<String> libraryImports = new PosLookup<>();
    private final PosLookup<Variable> variableReferences = new PosLookup<>();
    private final PosLookup<PropertyAccessExpression> propertyAccesses = new PosLookup<>();
    private final PosLookup<Scope> scopes = new PosLookup<>();

    private final TreeMetadata metadata;

    private Lookups(TreeMetadata metadata) {
        this.metadata = metadata;
    }

    public static Lookups get(Program program, TreeMetadata metadata) {
        var lookups = new Lookups(metadata);
        lookups.search(program);
        return lookups;
    }

    private void search(ProgramNode node) {
        metadata.get(node, MetadataKey.FULL_POS)
                .ifPresent(pos -> metadata.get(node, VariableAnalyser.SCOPE)
                        .ifPresent(scope -> scopes.add(pos, scope)));

        switch (node) {
            case ImportStatement statement -> {
                metadata.get(statement, MetadataKey.IMPORT_LOCATION_POS)
                        .ifPresent(pos -> libraryImports.add(pos, statement.libraryName()));

                checkVariableRef(statement);
            }
            case VariableAccessExpression expression -> {
                var pos = metadata.get(expression, MetadataKey.MAIN_POS);
                var variable = metadata.get(expression, VariableAnalyser.VARIABLE_REFERENCE);
                if (pos.isPresent() && variable.isPresent()) {
                    variableReferences.add(pos.get(), variable.get());
                }
            }
            case VariableCreationStatement statement -> {
                checkVariableRef(statement);
                statement.getChildren().forEach(this::search);
            }
            case FunctionDeclarationStatement statement -> {
                checkVariableRef(statement);
                statement.getChildren().forEach(this::search);
            }
            case FunctionArgument argument -> {
                checkVariableRef(argument);
                argument.getChildren().forEach(this::search);
            }
            case ForEachLoopStatement statement -> {
                checkVariableRef(statement);
                statement.getChildren().forEach(this::search);
            }
            case PropertyAccessExpression expression -> {
                metadata.get(expression, MetadataKey.NAME_POS)
                        .ifPresent(pos -> propertyAccesses.add(pos, expression));
                expression.getChildren().forEach(this::search);
            }
            case ProgramNode other -> other.getChildren().forEach(this::search);
        }
    }

    private void checkVariableRef(ProgramNode node) {
        var namePos = metadata.get(node, MetadataKey.NAME_POS);
        var variable = metadata.get(node, VariableAnalyser.VARIABLE_REFERENCE);
        if (namePos.isPresent() && variable.isPresent()) {
            variableReferences.add(namePos.get(), variable.get());
        }
    }

    public TreeMetadata treeMetadata() {
        return metadata;
    }

    public PosLookup<String> libraryImports() {
        return libraryImports;
    }

    public PosLookup<Variable> variableReferences() {
        return variableReferences;
    }

    public PosLookup<PropertyAccessExpression> propertyAccesses() {
        return propertyAccesses;
    }

    public PosLookup<Scope> scopes() {
        return scopes;
    }
}

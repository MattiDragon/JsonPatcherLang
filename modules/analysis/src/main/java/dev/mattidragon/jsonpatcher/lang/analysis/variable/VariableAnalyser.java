package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.*;
import java.util.function.Predicate;

/**
 * Generic variable analyser for jsonpatcher meant to serve both the language server and the bytecode compiler.
 */
public class VariableAnalyser {
    public static final MetadataKey<Variable> VARIABLE_REFERENCE = new MetadataKey<>("VariableAnalyser/VARIABLE_REFERENCE");
    public static final MetadataKey<RootVariable> ROOT_REFERENCE = new MetadataKey<>("VariableAnalyser/ROOT_REFERENCE");
    public static final MetadataKey<Scope> SCOPE = new MetadataKey<>("VariableAnalyser/SCOPE");
    
    private final TreeMetadata metadata;
    private final Map<VariableAccessExpression, MutableScope> missingVariables = new IdentityHashMap<>();
    private final Map<String, List<Variable>> earlyAccessVariables = new HashMap<>();
    private final List<Scope> scopes = new ArrayList<>();
    private final DiagnosticsBuilder diagnostics;

    private VariableAnalyser(TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.diagnostics = diagnostics;
    }

    /**
     * Analyses a given program for variables,
     * attaching metadata to variable references and returning results.
     * @param program The program to analyse
     * @param metadata The metadata object that variable metadata is added to
     * @param diagnostics A diagnostic builder to which diagnostics are added
     * @param globals A list of global variable names to consider
     * @return A record containing errors and miscellaneous information
     */
    public static VariableAnalysis analyse(Program program, TreeMetadata metadata, DiagnosticsBuilder diagnostics, Collection<String> globals) {
        var analyser = new VariableAnalyser(metadata, diagnostics);
        analyser.analyse(program, globals);
        return new VariableAnalysis(Collections.unmodifiableList(analyser.scopes));
    }

    private void analyse(Program program, Collection<String> globals) {
        var globalScope = new ProgramScope(program);
        scopes.add(globalScope);
        metadata.put(program, SCOPE, globalScope);
        for (var global : globals) {
            globalScope.define(new Variable(global, false, program));
        }
        analyseAll(program.getChildren(), globalScope);
        missingVariables.forEach((access, scope) -> {
            var name = access.name();
            var variable = scope.find(name);
            if (variable == null) {
                diagnostics.addDiagnostic(VariableAnalysisDiagnostic.missingVariable(name, metadata.get(access, MetadataKey.MAIN_POS).orElse(null), access));
            } else {
                diagnostics.addDiagnostic(VariableAnalysisDiagnostic.unavailableVariable(name, metadata.get(access, MetadataKey.MAIN_POS).orElse(null), access));
                // Attach the variable anyway for lsp as we know what the user intended
                metadata.put(access, VARIABLE_REFERENCE, variable);
            }
        });
        verifyMutations(program);
        checkUnused();
    }

    private void analyseAll(Iterable<? extends ProgramNode> nodes, MutableScope current) {
        for (var node : nodes) {
            analyse(node, current);
        }
    }

    private void analyse(ProgramNode node, MutableScope current) {
        switch (node) {
            case BlockStatement block -> {
                var scope = new BlockScope(block, current);
                scopes.add(scope);
                metadata.put(block, SCOPE, scope);
                analyseAll(block.getChildren(), scope);
            }
            case ApplyStatement statement -> {
                analyse(statement.root(), current);
                var scope = new ApplyScope(statement, current);
                scopes.add(scope);
                metadata.put(statement, SCOPE, scope);
                analyse(statement.action(), scope);
            }
            case FunctionExpression function -> {
                var scope = new FunctionScope(function, current, buildEarlyAccessVariables());
                scopes.add(scope);
                metadata.put(function, SCOPE, scope);
                analyse(function.args(), scope);
                analyse(function.body(), scope);
            }
            case FunctionArgument argument -> {
                var functionScope = (FunctionScope) current;
                switch (argument.target()) {
                    case FunctionArgument.Target.Variable target -> {
                        var variable = new Variable(target.name(), true, argument);
                        metadata.put(argument, VARIABLE_REFERENCE, variable);
                        functionScope.variables().add(variable);
                    }
                    case FunctionArgument.Target.Root.INSTANCE -> metadata.put(argument, ROOT_REFERENCE, functionScope.root());
                }

                argument.defaultValue().ifPresent(defaultValue -> analyse(defaultValue, functionScope));
            }
            case VariableCreationStatement statement -> {
                var variable = new Variable(statement.name(), statement.mutable(), statement);
                addEarlyAccess(variable);
                analyse(statement.initializer(), current);
                removeEarlyAccess(variable);
                define(variable, current, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
            }
            case ImportStatement statement -> {
                var variable = new Variable(statement.variableName(), false, statement);
                define(variable, current, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
            }
            case FunctionDeclarationStatement statement -> {
                var variable = new Variable(statement.name(), false, statement);
                addEarlyAccess(variable);
                analyse(statement.value(), current);
                removeEarlyAccess(variable);
                define(variable, current, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
            }
            case ForEachLoopStatement statement -> {
                analyse(statement.iterable(), current);
                var scope = new BlockScope(statement, current);
                metadata.put(statement, SCOPE, scope);
                scopes.add(scope);
                var variable = new Variable(statement.variableName(), false, statement);
                define(variable, scope, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
                analyse(statement.body(), scope);
            }
            case ForLoopStatement statement -> {
                var scope = new BlockScope(statement, current);
                scopes.add(scope);
                analyse(statement.initializer(), scope);
                analyse(statement.condition(), scope);
                analyse(statement.body(), scope);
                analyse(statement.incrementer(), scope);
            }
            case WhileLoopStatement statement -> {
                var scope = new BlockScope(statement, current);
                scopes.add(scope);
                analyse(statement.condition(), current);
                analyse(statement.body(), scope);
            }
            case IfStatement statement -> {
                var scope = new BlockScope(statement, current);
                scopes.add(scope);
                analyse(statement.condition(), current);
                analyse(statement.action(), scope);
                if (statement.elseAction() != null) {
                    var elseScope = new BlockScope(statement, current);
                    scopes.add(scope);
                    analyse(statement.elseAction(), elseScope);
                }
            }
            case VariableAccessExpression access -> {
                switch (current.find(access.name())) {
                    case Variable variable -> {
                        metadata.put(access, VARIABLE_REFERENCE, variable);
                        variable.addUsage(access);
                    }
                    // Process missing variables later in order to get better errors
                    case null -> missingVariables.put(access, current);
                }
            }
            case RootExpression expression -> metadata.put(expression, ROOT_REFERENCE, current.root());
            default -> analyseAll(node.getChildren(), current);
        }
    }

    private void addEarlyAccess(Variable variable) {
        earlyAccessVariables.computeIfAbsent(variable.name(), k -> new ArrayList<>()).add(variable);
    }

    private void removeEarlyAccess(Variable variable) {
        var list = earlyAccessVariables.get(variable.name());
        if (list != null) {
            list.remove(variable);
            if (list.isEmpty()) {
                earlyAccessVariables.remove(variable.name());
            }
        }
    }

    private List<Variable> buildEarlyAccessVariables() {
        return earlyAccessVariables.values().stream()
                .filter(Predicate.not(List::isEmpty))
                .map(List::getLast)
                .toList();
    }

    // Side effect: marks mutated variables
    private void verifyMutations(ProgramNode node) {
        switch (node) {
            case AssignmentExpression(VariableAccessExpression accessExpression, var value, var op) -> checkMutation(accessExpression);
            case UnaryModificationExpression(var postfix, VariableAccessExpression accessExpression, var op) -> checkMutation(accessExpression);
            default -> {}
        }
        node.getChildren().forEach(this::verifyMutations);
    }

    // Side effect: marks mutated variables
    private void checkMutation(VariableAccessExpression accessExpression) {
        var variable = metadata.get(accessExpression, VARIABLE_REFERENCE);
        if (variable.isEmpty()) {
            return;
        }
        if (!variable.get().mutable()) {
            diagnostics.addDiagnostic(VariableAnalysisDiagnostic.illegalMutation(variable.get().name(), metadata.get(accessExpression, MetadataKey.MAIN_POS).orElse(null), accessExpression));
        }
        variable.get().markMutated();
    }

    private void define(Variable variable, MutableScope scope, ProgramNode node) {
        if (scope.has(variable.name())) {
            diagnostics.addDiagnostic(VariableAnalysisDiagnostic.duplicateVariable(variable.name(), metadata.get(node, MetadataKey.MAIN_POS).orElse(null), node));
        }
        scope.define(variable);
    }

    // Relies on mutation marking from verifyMutations
    private void checkUnused() {
        for (var scope : scopes) {
            for (var variable : scope.variables()) {
                var definition = variable.definition();
                // Unused arguments get a pass as they are api
                if (definition instanceof FunctionArgument) continue;
                // Unused globals don't matter
                if (definition instanceof Program) continue;

                var namePos = metadata.get(definition, MetadataKey.NAME_POS)
                        .or(() -> metadata.get(definition, MetadataKey.MAIN_POS))
                        .orElse(null);
                var varKeywordPos = metadata.get(definition, MetadataKey.KEYWORD_POS)
                        .or(() -> metadata.get(definition, MetadataKey.MAIN_POS))
                        .orElse(null);

                if (variable.usages().isEmpty()) {
                    diagnostics.addDiagnostic(VariableAnalysisDiagnostic.unusedVariable(variable.name(), namePos, definition));
                } else if (variable.mutable() && !variable.isMutated()) {
                    diagnostics.addDiagnostic(VariableAnalysisDiagnostic.unnecessaryMutability(variable.name(), varKeywordPos, definition));
                }
            }
        }
    }
}

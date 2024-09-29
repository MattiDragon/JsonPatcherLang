package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.RootExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Generic variable analyser for jsonpatcher meant to serve both the language server and the bytecode compiler.
 */
public class VariableAnalyser {
    public static final MetadataKey<Variable> VARIABLE_REFERENCE = new MetadataKey<>("VariableAnalyser/VARIABLE_REFERENCE");
    public static final MetadataKey<RootVariable> ROOT_REFERENCE = new MetadataKey<>("VariableAnalyser/ROOT_REFERENCE");
    public static final MetadataKey<Scope> SCOPE = new MetadataKey<>("VariableAnalyser/SCOPE");
    
    private final TreeMetadata metadata;
    private final Map<VariableAccessExpression, LazyRef> lazyRefs = new HashMap<>();
    private final List<Scope> scopes = new ArrayList<>();
    private final List<AnalysisError> errors = new ArrayList<>();

    private VariableAnalyser(TreeMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Analyses a given program for variables,
     * attaching metadata to variable references and returning results.
     * @param program The program to analyse
     * @param metadata The metadata object that variable metadata is added to
     * @param globals A list of global variable names to consider
     * @return A record containing errors and miscellaneous information
     */
    public static VariableAnalysis analyse(Program program, TreeMetadata metadata, Collection<String> globals) {
        var analyser = new VariableAnalyser(metadata);
        analyser.analyse(program, globals);
        return new VariableAnalysis(Collections.unmodifiableList(analyser.errors), Collections.unmodifiableList(analyser.scopes));
    }

    private void analyse(Program program, Collection<String> globals) {
        var globalScope = new ProgramScope(program);
        metadata.put(program, SCOPE, globalScope);
        for (var global : globals) {
            globalScope.define(new Variable(global, false, program));
        }
        analyseAll(program.getChildren(), globalScope);
        lazyRefs.forEach((access, lazyRef) -> {
            lazyRef.resolve();
            var variable = lazyRef.getValue();
            if (variable == null) {
                errors.add(new AnalysisError.MissingVariable(lazyRef.getName(), access, metadata.get(access, MetadataKey.MAIN_POS).orElse(null)));
                return;
            }
            variable.addUsage(access);
            metadata.put(access, VARIABLE_REFERENCE, variable);
        });
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
                var scope = new FunctionScope(function, current);
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
            }
            case VariableCreationStatement statement -> {
                analyse(statement.initializer(), current);
                var variable = new Variable(statement.name(), statement.mutable(), statement);
                define(variable, current, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
            }
            case ImportStatement statement -> {
                var variable = new Variable(statement.variableName(), false, statement);
                define(variable, current, statement);
                metadata.put(statement, VARIABLE_REFERENCE, variable);
            }
            case FunctionDeclarationStatement statement -> {
                analyse(statement.value(), current);
                var variable = new Variable(statement.name(), false, statement);
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
                    case LazyRef lazyRef -> lazyRefs.put(access, lazyRef);
                    case Variable variable -> {
                        metadata.put(access, VARIABLE_REFERENCE, variable);
                        variable.addUsage(access);
                    }
                    case null -> errors.add(new AnalysisError.MissingVariable(access.name(), access, metadata.get(access, MetadataKey.MAIN_POS).orElse(null)));
                }
            }
            case RootExpression expression -> metadata.put(expression, ROOT_REFERENCE, current.root());
            default -> analyseAll(node.getChildren(), current);
        }
    }
    
    private void define(Variable variable, MutableScope scope, ProgramNode node) {
        if (scope.has(variable.name())) {
            errors.add(new AnalysisError.DuplicateVariable(variable.name(), node, metadata.get(node, MetadataKey.MAIN_POS).orElse(null)));
        }
        scope.define(variable);
    }

    public static abstract sealed class AnalysisError {
        private final String variableName;
        private final ProgramNode node;
        private final @Nullable SourceSpan pos;
        
        private AnalysisError(String variableName, ProgramNode node, @Nullable SourceSpan pos) {
            this.variableName = variableName;
            this.node = node;
            this.pos = pos;
        }

        public String getVariableName() {
            return variableName;
        }

        public ProgramNode getNode() {
            return node;
        }

        public @Nullable SourceSpan getPos() {
            return pos;
        }

        public static final class MissingVariable extends AnalysisError {
            private MissingVariable(String variableName, ProgramNode node, @Nullable SourceSpan pos) {
                super(variableName, node, pos);
            }
        }
        
        public static final class DuplicateVariable extends AnalysisError {
            private DuplicateVariable(String variableName, ProgramNode node, @Nullable SourceSpan pos) {
                super(variableName, node, pos);
            }
        }
    }
}

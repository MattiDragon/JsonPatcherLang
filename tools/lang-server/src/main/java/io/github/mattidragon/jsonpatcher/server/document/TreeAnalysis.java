package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.FunctionExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.VariableAccessExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArgument;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TreeAnalysis {
    public static final Scope GLOBAL_SCOPE = new Scope(null, true, List.of(
            new Variable("debug", Variable.Kind.IMPORT, false, true, null),
            new Variable("math", Variable.Kind.IMPORT, false, true, null),
            new Variable("objects", Variable.Kind.IMPORT, false, true, null),
            new Variable("arrays", Variable.Kind.IMPORT, false, true, null),
            new Variable("functions", Variable.Kind.IMPORT, false, true, null),
            new Variable("strings", Variable.Kind.IMPORT, false, true, null),
            new Variable("metapatch", Variable.Kind.IMPORT, false, true, null),
            new Variable("_isLibrary", Variable.Kind.LOCAL, false, true, null),
            new Variable("_target", Variable.Kind.LOCAL, false, true, null),
            new Variable("_isMetapatch", Variable.Kind.LOCAL, false, true, null)
    ));
    private final Map<ProgramNode, Scope> containingScopes = new HashMap<>();
    private final Map<VariableAccessExpression, Variable> variableMappings = new HashMap<>();
    private final Map<VariableAccessExpression, Scope> unboundVariables = new HashMap<>();
    private final Program tree;

    public TreeAnalysis(Program tree) {
        this.tree = tree;
        analyse(tree, GLOBAL_SCOPE.child());
        resolveLateVariables();
    }
    
    @Nullable
    public Variable getVariableDefinition(VariableAccessExpression expression) {
        return variableMappings.get(expression); 
    }
    
    public Collection<VariableAccessExpression> getUnboundVariables() {
        return unboundVariables.keySet();
    }
    
    private void analyse(ProgramNode node, Scope currentScope) {
        containingScopes.put(node, currentScope);
        switch (node) {
            case ImportStatement statement -> 
                    currentScope.definitions.add(Variable.ofImport(statement.variableName(), statement.namePos()));
            case VariableCreationStatement statement -> {
                analyse(statement.initializer(), currentScope);
                currentScope.definitions.add(Variable.ofLocal(statement.name(), statement.mutable(), statement.namePos()));
            }
            case FunctionDeclarationStatement statement -> {
                analyse(statement.getChildren(), currentScope);
                currentScope.definitions.add(Variable.ofFunction(statement.name(), statement.namePos()));
            }
            case FunctionArgument argument -> {
                argument.defaultValue().ifPresent(expression -> analyse(expression, currentScope));
                if (argument.target() instanceof FunctionArgument.Target.Variable variable) {
                    currentScope.definitions.add(Variable.ofParameter(variable.name(), argument.namePos()));
                }
            }
            
            case FunctionExpression expression -> {
                var scope = currentScope.capturingChild();
                analyse(expression.args(), scope);
                analyse(expression.body(), scope);
            }
            case BlockStatement statement -> {
                var scope = currentScope.child();
                analyse(statement.statements(), scope);
            }
            case ForEachLoopStatement statement -> {
                var scope = currentScope.child();
                scope.definitions.add(Variable.ofLocal(statement.variableName(), false, statement.variablePos()));
                analyse(statement.body(), scope);
            }
            case ForLoopStatement statement -> {
                var scope = currentScope.child();
                analyse(statement.initializer(), scope);
                analyse(statement.body(), scope);
                analyse(statement.condition(), scope);
                analyse(statement.incrementer(), scope);
            }
            
            case VariableAccessExpression expression -> {
                var variable = resolveVariable(expression.name(), currentScope);
                if (variable != null) {
                    variableMappings.put(expression, variable);
                } else {
                    unboundVariables.put(expression, currentScope);
                }
            }
            
            default -> node.getChildren().forEach(child -> analyse(child, currentScope));
        }
    }
    
    // Resolves variables in cases where they are allowed to be declared after usage (lambda captures)
    private void resolveLateVariables() {
        for (var iterator = unboundVariables.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var key = entry.getKey();
            var scope = entry.getValue();
            var name = key.name();
            
            while (scope != null) {
                if (!scope.immediate) {
                    scope = scope.parent;
                    break;
                }
                scope = scope.parent;
            }
            
            while (scope != null) {
                var variable = scope.definitions.stream().filter(candidate -> candidate.name.equals(name)).findFirst();
                if (variable.isPresent()) {
                    variableMappings.put(key, variable.get());
                    iterator.remove();
                    break;
                }
                scope = scope.parent;
            }
        }
    }
    
    @Nullable
    private Variable resolveVariable(String name, Scope scope) {
        return scope.definitions.stream().filter(variable -> variable.name.equals(name)).findFirst()
                .or(() -> Optional.ofNullable(scope.parent).map(parent -> resolveVariable(name, parent)))
                .orElse(null);
    }
    
    private void analyse(Iterable<? extends ProgramNode> nodes, Scope scope) {
        for (var node : nodes) {
            analyse(node, scope);
        }
    }

    public Program getTree() {
        return tree;
    }

    public record Scope(@Nullable Scope parent, boolean immediate, List<Variable> definitions) {
        public Scope child() {
            return new Scope(this, true, new ArrayList<>());
        }
        
        public Scope capturingChild() {
            return new Scope(this, false, new ArrayList<>());
        }
    }
    
    public record Variable(String name, Kind kind, boolean mutable, boolean stdlib, @Nullable SourceSpan definitionPos) {
        public static Variable ofImport(String name, @Nullable SourceSpan pos) {
            return new Variable(name, Kind.IMPORT, false, false, pos);
        }
        public static Variable ofFunction(String name, @Nullable SourceSpan pos) {
            return new Variable(name, Kind.FUNCTION, false, false, pos);
        }
        public static Variable ofParameter(String name, @Nullable SourceSpan pos) {
            return new Variable(name, Kind.PARAMETER, false, false, pos);
        }
        public static Variable ofLocal(String name, boolean mutable, @Nullable SourceSpan pos) {
            return new Variable(name, Kind.LOCAL, mutable, false, pos);
        }
        
        public enum Kind {
            IMPORT,
            FUNCTION,
            LOCAL,
            PARAMETER
        }
    }
}
package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.FunctionExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.PropertyAccessExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.VariableAccessExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArgument;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TreeAnalysis {
    public static final Scope GLOBAL_SCOPE;

    static {
        var definitions = new ArrayList<VariableDefinition>();
        GLOBAL_SCOPE = new Scope(null, true, Collections.unmodifiableList(definitions));
        definitions.addAll(List.of(
                new ImportDefinition("debug", true, null),
                new ImportDefinition("math", true, null),
                new ImportDefinition("objects", true, null),
                new ImportDefinition("arrays", true, null),
                new ImportDefinition("functions", true, null),
                new ImportDefinition("strings", true, null),
                new ImportDefinition("metapatch", true, null),
                new LocalDefinition("_isLibrary", false, true, null),
                new LocalDefinition("_target", false, true, null),
                new LocalDefinition("_isMetapatch", false, true, null)));
    }

    private final PosLookup<String> imports = new PosLookup<>();
    private final PosLookup<VariableDefinition> variableReferences = new PosLookup<>();
    private final PosLookup<PropertyAccessExpression> propertyAccesses = new PosLookup<>();
    private final HashSet<VariableDefinition> unusedVariables = new HashSet<>();
    private final Map<VariableAccessExpression, VariableDefinition> variableMappings = new HashMap<>();
    private final Map<VariableAccessExpression, Scope> unresolvedVariables = new HashMap<>();
    private final Program tree;

    public TreeAnalysis(Program tree) {
        this.tree = tree;
        analyse(tree, GLOBAL_SCOPE.child());
        resolveLateVariables();
    }

    private void analyse(ProgramNode node, Scope currentScope) {
        switch (node) {
            case ImportStatement statement -> {
                var variable = VariableDefinition.ofImport(statement.variableName(), statement);
                addVariable(currentScope, variable);
                imports.add(statement.namePos(), statement.libraryName());
            }
            case VariableCreationStatement statement -> {
                analyse(statement.initializer(), currentScope);
                addVariable(currentScope,
                        VariableDefinition.ofLocal(statement.name(), statement.mutable(), statement.namePos()));
            }
            case FunctionDeclarationStatement statement -> {
                analyse(statement.getChildren(), currentScope);
                addVariable(currentScope, VariableDefinition.ofFunction(statement.name(), statement));
            }
            case FunctionArgument argument -> {
                argument.defaultValue().ifPresent(expression -> analyse(expression, currentScope));
                if (argument.target() instanceof FunctionArgument.Target.Variable variable) {
                    addVariable(currentScope, VariableDefinition.ofParameter(variable.name(), argument));
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
                addVariable(scope,
                        VariableDefinition.ofLocal(statement.variableName(), false, statement.variablePos()));
                analyse(statement.getChildren(), scope);
            }
            case ForLoopStatement statement -> {
                var scope = currentScope.child();
                analyse(statement.initializer(), scope);
                analyse(statement.body(), scope);
                analyse(statement.condition(), scope);
                analyse(statement.incrementer(), scope);
            }

            case VariableAccessExpression expression -> {
                var variable = resolveVariable(expression.name(), currentScope, expression.pos());
                if (variable != null) {
                    variableMappings.put(expression, variable);
                } else {
                    unresolvedVariables.put(expression, currentScope);
                }
            }
            case PropertyAccessExpression expression -> {
                propertyAccesses.add(expression.namePos(), expression);
                analyse(expression.parent(), currentScope);
            }

            default -> node.getChildren().forEach(child -> analyse(child, currentScope));
        }
    }

    private void addVariable(Scope currentScope, VariableDefinition variable) {
        currentScope.definitions.add(variable);
        var pos = variable.definitionPos();
        if (pos != null) {
            variableReferences.add(pos, variable);
        }
        unusedVariables.add(variable);
    }

    // Resolves variables in cases where they are allowed to be declared after usage
    // (lambda captures)
    private void resolveLateVariables() {
        for (var iterator = unresolvedVariables.entrySet().iterator(); iterator.hasNext();) {
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
                var variable = scope.definitions.stream().filter(candidate -> candidate.name().equals(name))
                        .findFirst();
                if (variable.isPresent()) {
                    variableMappings.put(key, variable.get());
                    variableReferences.add(key.pos(), variable.get());
                    unusedVariables.remove(variable.get());
                    iterator.remove();
                    break;
                }
                scope = scope.parent;
            }
        }
    }

    @Nullable
    private VariableDefinition resolveVariable(String name, Scope scope, SourceSpan pos) {
        var variable = scope.definitions.stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .or(() -> Optional.ofNullable(scope.parent).map(parent -> resolveVariable(name, parent, pos)))
                .orElse(null);
        if (variable != null) {
            unusedVariables.remove(variable);
            variableReferences.add(pos, variable);
        }
        return variable;
    }

    private void analyse(Iterable<? extends ProgramNode> nodes, Scope scope) {
        for (var node : nodes) {
            analyse(node, scope);
        }
    }

    /**
     * Gets information about the definition of the variable that a specific access
     * expression is using.
     * 
     * @return The variable definition, or {@code null} if it couldn't be resolved.
     */
    @Nullable
    public VariableDefinition getVariableDefinition(VariableAccessExpression expression) {
        return variableMappings.get(expression);
    }

    /**
     * Returns all the variables accesses whose definitions couldn't be resolved.
     */
    public Collection<VariableAccessExpression> getUnresolvedVariables() {
        return unresolvedVariables.keySet();
    }

    /**
     * Returns all the variables that were determined to not be used anywhere.
     */
    public Collection<VariableDefinition> getUnusedVariables() {
        return unusedVariables;
    }

    /**
     * Returns a {@link PosLookup} for the locations of import locations.
     * {@snippet lang=jsonpatcher : 
     * # @highlight regex=".library_name." :
     * import "library_name" as variableName;
     * }
     */
    public PosLookup<String> getImportedModules() {
        return imports;
    }

    /**
     * Returns a {@link PosLookup} for looking up variable definitions based on reference locations.
     */
    public PosLookup<VariableDefinition> getVariableReferences() {
        return variableReferences;
    }

    /**
     * Returns a {@link PosLookup} for looking up variable property expressions at their location.
     */
    public PosLookup<PropertyAccessExpression> getPropertyAccesses() {
        return propertyAccesses;
    }

    /**
     * Returns the program tree originally passed to this analysis.
     */
    public Program getTree() {
        return tree;
    }

    public record Scope(@Nullable Scope parent, boolean immediate, List<VariableDefinition> definitions) {
        public Scope child() {
            return new Scope(this, true, new ArrayList<>());
        }

        public Scope capturingChild() {
            return new Scope(this, false, new ArrayList<>());
        }

        @Override
        public String toString() {
            return "Scope[parent=%s, immediate=%s]".formatted(parent, immediate);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    public sealed interface VariableDefinition {
        static VariableDefinition ofImport(String name, @Nullable ImportStatement statement) {
            return new ImportDefinition(name, false, statement);
        }

        static VariableDefinition ofFunction(String name, @Nullable FunctionDeclarationStatement statement) {
            return new FunctionDefinition(name, false, statement);
        }

        static VariableDefinition ofParameter(String name, @Nullable FunctionArgument argument) {
            return new ParameterDefinition(name, argument);
        }

        static VariableDefinition ofLocal(String name, boolean mutable, @Nullable SourceSpan pos) {
            return new LocalDefinition(name, mutable, false, pos);
        }

        String name();

        default boolean mutable() {
            return false;
        }

        boolean stdlib();

        @Nullable
        SourceSpan definitionPos();
    }

    public record ImportDefinition(String name, boolean stdlib, @Nullable ImportStatement statement)
            implements VariableDefinition {
        @Override
        public @Nullable SourceSpan definitionPos() {
            if (statement == null)
                return null;
            return statement.variablePos();
        }
    }

    public record FunctionDefinition(String name, boolean stdlib, @Nullable FunctionDeclarationStatement statement)
            implements VariableDefinition {
        @Override
        public @Nullable SourceSpan definitionPos() {
            if (statement == null)
                return null;
            return statement.namePos();
        }
    }

    public record ParameterDefinition(String name, @Nullable FunctionArgument argument) implements VariableDefinition {
        @Override
        public boolean stdlib() {
            return false;
        }

        @Override
        public @Nullable SourceSpan definitionPos() {
            if (argument == null)
                return null;
            return argument.namePos();
        }
    }

    public record LocalDefinition(String name, boolean mutable, boolean stdlib, @Nullable SourceSpan definitionPos)
            implements VariableDefinition {
    }
}

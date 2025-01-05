package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.AssignmentExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.PropertyAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class TreeAnalysis {
    public static final Scope GLOBAL_SCOPE;

    static {
        var definitions = new ArrayList<VariableDefinition>();
        GLOBAL_SCOPE = new Scope(null, true, Collections.unmodifiableList(definitions));
        definitions.addAll(List.of(
                new ImportDefinition("debug", true, null, null),
                new ImportDefinition("math", true, null, null),
                new ImportDefinition("objects", true, null, null),
                new ImportDefinition("arrays", true, null, null),
                new ImportDefinition("functions", true, null, null),
                new ImportDefinition("strings", true, null, null),
                new ImportDefinition("metapatch", true, null, null),
                new LocalDefinition("_isLibrary", false, true, null),
                new LocalDefinition("_target", false, true, null),
                new LocalDefinition("_isMetapatch", false, true, null)));
    }

    private final PosLookup<String> imports = new PosLookup<>();
    private final PosLookup<VariableDefinition> variableReferences = new PosLookup<>();
    private final PosLookup<PropertyAccessExpression> propertyAccesses = new PosLookup<>();
    private final HashSet<VariableDefinition> unusedVariables = new HashSet<>();
    private final Map<VariableAccessExpression, VariableDefinition> variableMappings = new HashMap<>();
    private final List<VariableDefinition> redefinitions = new ArrayList<>();
    private final Map<VariableAccessExpression, Scope> unresolvedVariables = new HashMap<>();
    private final List<VariableAccessExpression> mutations = new ArrayList<>();
    private final List<VariableAccessExpression> illegalMutations = new ArrayList<>();
    private final Program tree;
    private final TreeMetadata metadata;

    public TreeAnalysis(Program tree, TreeMetadata metadata) {
        this.tree = tree;
        this.metadata = metadata;
        analyse(tree, GLOBAL_SCOPE.child());
        resolveLateVariables();
        findIllegalMutations();
    }

    private void findIllegalMutations() {
        for (var mutation : mutations) {
            var definition = variableMappings.get(mutation);
            if (definition != null && !definition.mutable()) {
                illegalMutations.add(mutation);
            }
        }
    }

    private void analyse(ProgramNode node, Scope currentScope) {
        switch (node) {
            case ImportStatement statement -> {
                var variable = VariableDefinition.ofImport(statement.variableName(), statement, metadata);
                addVariable(currentScope, variable);
                metadata.get(statement, MetadataKey.NAME_POS)
                        .ifPresent(pos -> imports.add(pos, statement.libraryName()));
            }
            case VariableCreationStatement statement -> {
                analyse(statement.initializer(), currentScope);
                metadata.get(statement, MetadataKey.NAME_POS)
                        .ifPresent(pos ->
                                addVariable(currentScope, VariableDefinition.ofLocal(statement.name(), statement.mutable(), pos)));
            }
            case FunctionDeclarationStatement statement -> {
                analyse(statement.getChildren(), currentScope);
                addVariable(currentScope, VariableDefinition.ofFunction(statement.name(), statement, metadata));
            }
            case FunctionArgument argument -> {
                argument.defaultValue().ifPresent(expression -> analyse(expression, currentScope));
                if (argument.target() instanceof FunctionArgument.Target.Variable(var variableName)) {
                    addVariable(currentScope, VariableDefinition.ofParameter(variableName, argument, metadata));
                }
            }
            
            case AssignmentExpression(VariableAccessExpression target, var value, var operator) -> {
                mutations.add(target);
                analyse(target, currentScope);
                analyse(value, currentScope);
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

                metadata.get(statement, MetadataKey.NAME_POS)
                        .ifPresent(pos ->
                                addVariable(scope, VariableDefinition.ofLocal(statement.variableName(), false, pos)));
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
                var variable = resolveVariable(expression.name(), currentScope, metadata.get(expression, MetadataKey.MAIN_POS).orElse(null));
                if (variable != null) {
                    variableMappings.put(expression, variable);
                } else {
                    unresolvedVariables.put(expression, currentScope);
                }
            }
            case PropertyAccessExpression expression -> {
                metadata.get(expression, MetadataKey.NAME_POS)
                        .ifPresent(pos -> propertyAccesses.add(pos, expression));
                analyse(expression.parent(), currentScope);
            }

            default -> node.getChildren().forEach(child -> analyse(child, currentScope));
        }
    }

    private void addVariable(Scope currentScope, VariableDefinition variable) {
        checkRedefinition(currentScope, variable);
        
        currentScope.definitions.add(variable);
        var pos = variable.definitionPos();
        if (pos != null) {
            variableReferences.add(pos, variable);
        }
        unusedVariables.add(variable);
    }

    private void checkRedefinition(Scope scope, VariableDefinition variable) {
        do {
            if (scope.definitions.stream().anyMatch(def -> def.name().equals(variable.name()))) {
                redefinitions.add(variable);
                break;
            }
        } while ((scope = scope.parent()) != null);
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
                    metadata.get(key, MetadataKey.MAIN_POS)
                            .ifPresent(pos -> variableReferences.add(pos, variable.get()));
                    unusedVariables.remove(variable.get());
                    iterator.remove();
                    break;
                }
                scope = scope.parent;
            }
        }
    }

    @Nullable
    private VariableDefinition resolveVariable(String name, Scope scope, @Nullable SourceSpan pos) {
        var variable = scope.definitions.stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .or(() -> Optional.ofNullable(scope.parent).map(parent -> resolveVariable(name, parent, pos)))
                .orElse(null);
        if (variable != null) {
            unusedVariables.remove(variable);
            if (pos != null) {
                variableReferences.add(pos, variable);
            }
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
     * Returns a list of variable accesses that are involved in illegal mutation of immutable variables.
     */
    public List<VariableAccessExpression> getIllegalMutations() {
        return illegalMutations;     
    }

    /**
     * Returns the program tree originally passed to this analysis.
     */
    public Program getTree() {
        return tree;
    }

    public TreeMetadata getMetadata() {
        return metadata;
    }

    public List<VariableDefinition> getRedefinitions() {
        return redefinitions;
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
        static VariableDefinition ofImport(String name, @Nullable ImportStatement statement, TreeMetadata metadata) {
            var pos = statement == null ? null : metadata.get(statement, MetadataKey.NAME_POS).orElse(null);
            return new ImportDefinition(name, false, statement, pos);
        }

        static VariableDefinition ofFunction(String name, @Nullable FunctionDeclarationStatement statement, TreeMetadata metadata) {
            var pos = statement == null ? null : metadata.get(statement, MetadataKey.NAME_POS).orElse(null);
            return new FunctionDefinition(name, false, statement, pos);
        }

        static VariableDefinition ofParameter(String name, @Nullable FunctionArgument argument, TreeMetadata metadata) {
            var pos = argument == null ? null : metadata.get(argument, MetadataKey.NAME_POS).orElse(null);
            return new ParameterDefinition(name, argument, pos);
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

    public record ImportDefinition(String name, boolean stdlib, @Nullable ImportStatement statement, @Nullable SourceSpan definitionPos) 
            implements VariableDefinition {
    }

    public record FunctionDefinition(String name, boolean stdlib, @Nullable FunctionDeclarationStatement statement, @Nullable SourceSpan definitionPos)
            implements VariableDefinition {
    }

    public record ParameterDefinition(String name, @Nullable FunctionArgument argument, @Nullable SourceSpan definitionPos) implements VariableDefinition {
        @Override
        public boolean stdlib() {
            return false;
        }
    }

    public record LocalDefinition(String name, boolean mutable, boolean stdlib, @Nullable SourceSpan definitionPos)
            implements VariableDefinition {
    }
}

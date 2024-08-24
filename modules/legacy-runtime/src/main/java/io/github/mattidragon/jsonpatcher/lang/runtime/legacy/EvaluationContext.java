package io.github.mattidragon.jsonpatcher.lang.runtime.legacy;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import io.github.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import io.github.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import io.github.mattidragon.jsonpatcher.lang.runtime.ContextBuilder;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.LibraryLocator;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record EvaluationContext(Value.ObjectValue root, VariableStack variables, LibraryLocator libraryLocator, Consumer<Value> debugConsumer, LangConfig config,
                                TreeMetadata metadata) implements PatchFunction.BuiltInPatchFunction.Context {
    private static final ThreadLocal<Set<String>> LIBRARY_RECURSION_DETECTOR = ThreadLocal.withInitial(HashSet::new);

    public static Builder builder(LangConfig config, TreeMetadata metadata) {
        return new Builder(config, metadata);
    }

    public EvaluationContext withRoot(Value.ObjectValue root) {
        return new EvaluationContext(root, variables, libraryLocator, debugConsumer, config, metadata);
    }

    public EvaluationContext newScope() {
        return new EvaluationContext(root, new VariableStack(config, variables), libraryLocator, debugConsumer, config, metadata);
    }
    
    public Optional<SourceSpan> getPos(ProgramNode node) {
        return metadata.get(node, MetadataKey.MAIN_POS);
    }
    
    public <T> Optional<T> getMetadata(ProgramNode node, MetadataKey<T> key) {
        return metadata.get(node, key);
    }

    @Override
    public Value execute(PatchFunction function, List<Value> args, SourceSpan callPos) {
        return null;
    }

    public void log(Value value) {
        debugConsumer.accept(value);
    }

    public Value findLibrary(String libraryName, SourceSpan pos) {
        if (Libraries.LOOKUP.containsKey(libraryName)) {
            return Libraries.LOOKUP.get(libraryName).get();
        }
        if (Libraries.BUILTIN.containsKey(libraryName)) {
            throw new EvaluationException(config, "Cannot load builtin library %s. You don't need to import it.".formatted(libraryName), pos);
        }

        try {
            if (!LIBRARY_RECURSION_DETECTOR.get().add(libraryName)) {
                throw new EvaluationException(config, "Recursive library import detected for %s".formatted(libraryName), pos);
            }
            var json = new Value.ObjectValue();
            libraryLocator.loadLibrary(libraryName, json, pos, config());
            return json;
        } finally {
            LIBRARY_RECURSION_DETECTOR.get().remove(libraryName);
        }
    }

    public static class Builder implements ContextBuilder {
        private final LangConfig config;
        private final TreeMetadata metadata;
        private Value.ObjectValue root = new Value.ObjectValue();
        private final VariableStack variables;
        private LibraryLocator libraryLocator;
        private Consumer<Value> debugConsumer = x -> System.out.println("Debug from patch: " + x);
        private Map<String, Supplier<Value.ObjectValue>> stdlib = Libraries.BUILTIN;

        public Builder(LangConfig config, TreeMetadata metadata) {
            this.config = config;
            libraryLocator = (name, obj, pos, config1) -> {
                throw new EvaluationException(config1, "No libraries available", pos);
            };
            variables = new VariableStack(config);
            this.metadata = metadata;
        }

        @Override
        public Builder root(Value.ObjectValue root) {
            this.root = root;
            return this;
        }

        @Override
        public Builder variable(String name, String value) {
            return variable(name, new Value.StringValue(value));
        }

        @Override
        public Builder variable(String name, boolean value) {
            return variable(name, Value.BooleanValue.of(value));
        }

        @Override
        public Builder variable(String name, Value value) {
            variables.createVariable(name, value, false, null);
            return this;
        }

        @Override
        public Builder libraryLocator(LibraryLocator libraryLocator) {
            this.libraryLocator = libraryLocator;
            return this;
        }

        @Override
        public Builder debugConsumer(Consumer<Value> debugConsumer) {
            this.debugConsumer = debugConsumer;
            return this;
        }

        @Override
        public Builder stdlib(Map<String, Supplier<Value.ObjectValue>> stdlib) {
            this.stdlib = stdlib;
            return this;
        }

        public EvaluationContext build() {
            stdlib.forEach((name, supplier) -> variables.createVariable(name, supplier.get(), false, null));
            return new EvaluationContext(root, variables, libraryLocator, debugConsumer, config, metadata).newScope();
        }
    }
}

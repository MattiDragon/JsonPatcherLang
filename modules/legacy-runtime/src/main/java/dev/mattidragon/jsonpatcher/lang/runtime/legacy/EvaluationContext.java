package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionContext;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.ContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.LibraryLocator;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record EvaluationContext(Value.ObjectValue root, VariableStack variables, LibraryLocator libraryLocator, Consumer<Value> debugConsumer, LangConfig config,
                                TreeMetadata metadata) {
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

    public Value findLibrary(String libraryName, FunctionContext context) {
        if (Libraries.LOOKUP.containsKey(libraryName)) {
            return Libraries.LOOKUP.get(libraryName).get();
        }
        if (Libraries.BUILTIN.containsKey(libraryName)) {
            throw context.createException("Cannot load builtin library %s. You don't need to import it.".formatted(libraryName));
        }

        try {
            if (!LIBRARY_RECURSION_DETECTOR.get().add(libraryName)) {
                throw context.createException("Recursive library import detected for %s".formatted(libraryName));
            }
            var json = new Value.ObjectValue();
            libraryLocator.loadLibrary(libraryName, json, context);
            return json;
        } finally {
            LIBRARY_RECURSION_DETECTOR.get().remove(libraryName);
        }
    }
    
    public FunctionContext createFunctionContext(@Nullable SourceSpan pos) {
        return new FunctionContextWrapper(pos);
    }
    
    private class FunctionContextWrapper implements FunctionContext {
        private final @Nullable SourceSpan callPos;

        private FunctionContextWrapper(@Nullable SourceSpan callPos) {
            this.callPos = callPos;
        }

        @Override
        public RuntimeException createException(String message) {
            return new EvaluationException(config, message, callPos);
        }

        @Override
        public RuntimeException createException(String message, RuntimeException e) {
            return new EvaluationException(config, message, callPos);
        }

        @Override
        public LangConfig config() {
            return config;
        }

        @Override
        public Value execute(PatchFunction function, List<Value> args) {
            return switch (function) {
                case PatchFunction.BuiltInPatchFunction builtIn -> builtIn.execute(this, args);
                case LegacyRuntimePatchFunction scripted -> scripted.execute(EvaluationContext.this, args, callPos);
                case PatchFunction.RuntimePatchFunction other -> throw new IllegalStateException("Tried to run function from another runtime");
            };
        }

        @Override
        public void log(Value value) {
            debugConsumer.accept(value);
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
            libraryLocator = (name, obj, context) -> {
                throw context.createException("No libraries available");
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

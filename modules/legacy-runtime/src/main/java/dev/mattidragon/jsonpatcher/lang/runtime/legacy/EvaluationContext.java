package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.runtime.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.LibraryLocator;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.RuntimeContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public record EvaluationContext(Value.ObjectValue root, VariableStack variables, LibraryLocator libraryLocator, Consumer<Value> debugConsumer, LangConfig config,
                                TreeMetadata metadata) {
    private static final ThreadLocal<SequencedSet<String>> LIBRARY_RECURSION_DETECTOR = ThreadLocal.withInitial(LinkedHashSet::new);

    public static Builder builder(LangConfig config, TreeMetadata metadata, VariableHolder variables) {
        return new Builder(config, metadata, variables);
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

    public Value findLibrary(String libraryName, PlatformContext context) {
        if (Libraries.LOOKUP.containsKey(libraryName)) {
            return Libraries.LOOKUP.get(libraryName).get();
        }
        if (Libraries.BUILTIN.containsKey(libraryName)) {
            throw context.createException("Cannot load builtin library %s. You don't need to import it.".formatted(libraryName));
        }

        try {
            if (!LIBRARY_RECURSION_DETECTOR.get().add(libraryName)) {
                throw context.createException("Recursive library import detected: %s -> %s".formatted(String.join(" -> ", LIBRARY_RECURSION_DETECTOR.get()), libraryName));
            }
            var json = new Value.ObjectValue();
            libraryLocator.loadLibrary(libraryName, json, context);
            return json;
        } finally {
            LIBRARY_RECURSION_DETECTOR.get().remove(libraryName);
        }
    }
    
    public PlatformContext createFunctionContext(@Nullable SourceSpan pos) {
        return new PlatformContextWrapper(pos);
    }
    
    private class PlatformContextWrapper implements PlatformContext {
        private final @Nullable SourceSpan callPos;

        private PlatformContextWrapper(@Nullable SourceSpan callPos) {
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

    public static class Builder implements RuntimeContextBuilder {
        private final LangConfig config;
        private final TreeMetadata metadata;
        private final Set<String> declaredVars;
        private Value.ObjectValue root = new Value.ObjectValue();
        private final VariableStack variables;
        private LibraryLocator libraryLocator;
        private Consumer<Value> debugConsumer = x -> System.out.println("Debug from patch: " + x);

        public Builder(LangConfig config, TreeMetadata metadata, VariableHolder variableHolder) {
            this.config = config;
            libraryLocator = (name, obj, context) -> {
                throw context.createException("No libraries available");
            };
            variables = new VariableStack(config);
            this.declaredVars = new HashSet<>(variableHolder.getDeclared());
            this.metadata = metadata;
        }

        @Override
        public Builder root(Value.ObjectValue root) {
            this.root = root;
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
        public RuntimeContextBuilder fillVariable(String name, Value value) {
            if (!declaredVars.remove(name)) {
                throw new IllegalStateException("Tried to fill variable that wasn't declared: " + name);
            }
            variables.createVariable(name, value, false, null);
            return this;
        }

        public EvaluationContext build() {
            if (!declaredVars.isEmpty()) {
                throw new IllegalStateException("Variables declared, but not filled: " + String.join(", ", declaredVars));
            }
            return new EvaluationContext(root, variables, libraryLocator, debugConsumer, config, metadata).newScope();
        }
    }
}

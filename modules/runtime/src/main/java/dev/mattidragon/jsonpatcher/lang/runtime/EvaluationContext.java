package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryLookup;
import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.util.PropertyLookup;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.stdlib.Libraries;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

public record EvaluationContext(LangConfig config,
                                PropertyLookup propertyLookup,
                                LibraryLookup libraryLookup) implements PlatformContext {
    private static final ThreadLocal<SequencedSet<String>> LIBRARY_RECURSION_DETECTOR = ThreadLocal.withInitial(LinkedHashSet::new);

    @Override
    public RuntimeException createException(String message) {
        return new PatchException(message);
    }

    @Override
    public RuntimeException createException(String message, Exception cause) {
        return new PatchException(message, cause);
    }

    @Override
    public Value execute(PatchFunction function, List<Value> args) {
        return FunctionHooks.call(this, function, args.toArray(new Value[0]));
    }

    @Override
    public void log(Value value) {
        // TODO: impl
    }

    @Override
    public @Nullable Value getLibraryProperty(Value value, String property) {
        return propertyLookup.getProperty(value, property);
    }

    @SuppressWarnings("unused")
    public Value findLibrary(String libraryName) {
        if (Libraries.LOOKUP.containsKey(libraryName)) {
            return Libraries.LOOKUP.get(libraryName).get();
        }

        try {
            if (!LIBRARY_RECURSION_DETECTOR.get().add(libraryName)) {
                throw createException("Recursive library import detected: %s -> %s".formatted(String.join(" -> ", LIBRARY_RECURSION_DETECTOR.get()), libraryName));
            }
            return libraryLookup.findLibrary(libraryName);
        } finally {
            LIBRARY_RECURSION_DETECTOR.get().remove(libraryName);
        }
    }
}

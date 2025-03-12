package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryLookup;
import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.util.PropertyLookup;
import dev.mattidragon.jsonpatcher.lang.runtime.value.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

public record EvaluationContext(PropertyLookup propertyLookup, LibraryLookup libraryLookup) {
    private static final ThreadLocal<SequencedSet<String>> LIBRARY_RECURSION_DETECTOR = ThreadLocal.withInitial(LinkedHashSet::new);

    public RuntimeException createException(String message) {
        return new PatchException(message);
    }

    public RuntimeException createException(String message, Exception cause) {
        return new PatchException(message, cause);
    }

    public Value execute(PatchFunction function, List<Value> args) {
        return FunctionHooks.call(this, function, args.toArray(new Value[0]));
    }

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

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.LibraryLocator;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.PropertyLookup;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedSet;

public record EvaluationContext(LangConfig config,
                                PropertyLookup propertyLookup,
                                LibraryLocator libraryLocator) implements PlatformContext {
    private static final ThreadLocal<SequencedSet<String>> LIBRARY_RECURSION_DETECTOR = ThreadLocal.withInitial(LinkedHashSet::new);

    // TODO: custom exception
    @Override
    public RuntimeException createException(String message) {
        return new RuntimeException(message);
    }

    @Override
    public RuntimeException createException(String message, RuntimeException e) {
        return new RuntimeException(message, e);
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
    public Value getLibraryProperty(Value value, String property) {
        return propertyLookup.getProperty(value, property);
    }

    @SuppressWarnings("unused")
    public Value findLibrary(String libraryName) {
        if (Libraries.LOOKUP.containsKey(libraryName)) {
            return Libraries.LOOKUP.get(libraryName).get();
        }
        if (Libraries.BUILTIN.containsKey(libraryName)) {
            throw createException("Cannot load builtin library %s. You don't need to import it.".formatted(libraryName));
        }

        try {
            if (!LIBRARY_RECURSION_DETECTOR.get().add(libraryName)) {
                throw createException("Recursive library import detected: %s -> %s".formatted(String.join(" -> ", LIBRARY_RECURSION_DETECTOR.get()), libraryName));
            }
            var json = new Value.ObjectValue();
            libraryLocator.loadLibrary(libraryName, json, this);
            return json;
        } finally {
            LIBRARY_RECURSION_DETECTOR.get().remove(libraryName);
        }
    }
}

package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.List;

public sealed interface PatchFunction {
    default PatchFunction bind(Value value) {
        return Libraries.FunctionsLibrary.bind(new Value.FunctionValue(this), value).function();
    }

    @FunctionalInterface
    non-sealed interface BuiltInPatchFunction extends PatchFunction {
        Value execute(PlatformContext context, List<Value> args);
        
        default BuiltInPatchFunction argCount(int count) {
            return (context, args) -> {
                if (args.size() != count) {
                    throw context.createException("Incorrect function argument count: expected %s but found %s".formatted(count, args.size()));
                }
                return execute(context, args);
            };
        }

    }

    /**
     * A function defined created by a runtime. Only runtimes are allowed to create these,
     * and they must not be passed across different runtimes.
     */
    non-sealed interface RuntimePatchFunction extends PatchFunction {}
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.IncompatibleOperandsException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Collections;

public class FunctionHooks {
    private static final Class<?>[] FUNCTION_BODY_CLASSES = new Class[65];
    private static final MethodHandle[] FUNCTION_INVOKERS = new MethodHandle[65];
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle UNWRAP_OPTIONAL;
    private static final MethodHandle UNWRAP_VARARGS;

    static {
        try {
            UNWRAP_OPTIONAL = LOOKUP.findStatic(FunctionHooks.class, "unwrapOptional", MethodType.methodType(Value.class, Value[].class, int.class));
            UNWRAP_VARARGS = LOOKUP.findStatic(FunctionHooks.class, "unwrapVarargs", MethodType.methodType(Value.class, Value[].class, int.class));

            for (var clazz : FunctionBody.class.getClasses()) {
                var i = Integer.parseInt(clazz.getSimpleName().substring(1));
                FUNCTION_BODY_CLASSES[i] = clazz;
                FUNCTION_INVOKERS[i] = LOOKUP.findVirtual(clazz, "call", makeType(i));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot resolve method", e);
        }
    }

    /**
     * Creates a function object from a lambda object
     * @param impl An object supplying the implementation. Usually created via the lambda metafactory
     * @param requiredArgs The number of required arguments the function accepts. All arguments after that will be set to null if not specified, except for varargs, which will be empty
     * @param totalArgCount The number of arguments that should be passed to the implementation. Including optional and varargs parameters
     * @param varargs Whether the resulting 
     * @return A function value which ends up calling the body when called.
     */
    public static Value.FunctionValue createFunction(FunctionBody impl, int requiredArgs, int totalArgCount, boolean varargs) {
        if (!FUNCTION_BODY_CLASSES[totalArgCount].isAssignableFrom(impl.getClass())) throw new IllegalStateException("Wrong arg count specified");
        if (requiredArgs > totalArgCount) throw new IllegalStateException("Too many required args");
        if (requiredArgs == totalArgCount && varargs) throw new IllegalStateException("Too many required args (varargs must be optional)");

        var handle = FUNCTION_INVOKERS[totalArgCount].bindTo(impl);

        // Fill all optional parameters from an array. Each position receives its own copy of said array and picks out its value
        var filters = new MethodHandle[totalArgCount - requiredArgs];
        for (int i = 0; i < filters.length; i++) {
            filters[i] = MethodHandles.insertArguments(UNWRAP_OPTIONAL, 1, i);
        }
        if (varargs) {
            filters[filters.length - 1] = MethodHandles.insertArguments(UNWRAP_VARARGS, 1, totalArgCount - requiredArgs);
        }
        handle = MethodHandles.filterArguments(handle, requiredArgs, filters);
        
        // Use MethodHandles.permuteArguments to fill all array parameters from one parameter, which then gets marked varargs
        var reorder = new int[totalArgCount];
        for (int i = 0; i < requiredArgs; i++) {
            reorder[i] = i;
        }
        for (int i = requiredArgs; i < totalArgCount; i++) {
            reorder[i] = requiredArgs;
        }
        handle = MethodHandles.permuteArguments(handle, makeType(requiredArgs).appendParameterTypes(Value[].class), reorder);
        handle = handle.asVarargsCollector(Value[].class);
        
        return new Value.FunctionValue(new DefinedFunction(handle, requiredArgs, totalArgCount, varargs));
    }
    
    public static Value call(PlatformContext context, Value function, Value... args) {
        return switch (function) {
            case Value.FunctionValue(PatchFunction.BuiltInPatchFunction builtIn) -> builtIn.execute(context, Arrays.asList(args));
            case Value.FunctionValue(DefinedFunction definedFunction) -> definedFunction.call(args);
            case Value.FunctionValue(PatchFunction.RuntimePatchFunction other) -> throw new IllegalStateException("Tried to call function from another runtime: " + other);
            case Value other -> throw new IncompatibleOperandsException("%s is not callable".formatted(other));
        };
    }
    
    private static Value unwrapOptional(Value[] values, int index) {
        if (index < values.length) {
            return values[index];
        }
        return null;
    }
    
    private static Value unwrapVarargs(Value[] values, int optionalCount) {
        if (values.length <= optionalCount) return new Value.ArrayValue(Collections.emptyList());
        return new Value.ArrayValue(Arrays.asList(values).subList(optionalCount, values.length));
    }

    private static MethodType makeType(int argCount) {
        var argArray = new Class<?>[argCount];
        Arrays.fill(argArray, Value.class);
        return MethodType.methodType(Value.class, argArray);
    }

    public record DefinedFunction(MethodHandle handle, int requiredArgs, int totalArgCount, boolean varargs) implements PatchFunction.RuntimePatchFunction {
        public Value call(Value... args) {
            if (args.length < requiredArgs) throw new IllegalArgumentException("Too few arguments (required: %s, given: %s)".formatted(requiredArgs, args.length));
            if (!varargs && args.length > totalArgCount) throw new IllegalArgumentException("Too many arguments (allowed: %s, given: %s)".formatted(totalArgCount, args.length));
            
            try {
                return (Value) handle.invokeWithArguments((Object[]) args);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to call function", e);
            }
        }
    }
}

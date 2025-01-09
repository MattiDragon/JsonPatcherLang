package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.IncompatibleOperandsException;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class FunctionHooks {
    private static final Class<?>[] FUNCTION_BODY_CLASSES = new Class[65];
    private static final MethodHandle[] FUNCTION_INVOKERS = new MethodHandle[65];
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle UNWRAP_OPTIONAL;
    private static final MethodHandle UNWRAP_VARARGS;

    private static final MethodHandle INSTANCEOF_CHECK;
    private static final MethodHandle CALL_BUILTIN_FUNCTION;
    private static final MethodHandle ARRAY_AS_LIST;
    private static final MethodHandle DEFINED_FUNCTION_HANDLE;
    private static final MethodHandle GET_WRONG_RUNTIME_ERROR;
    private static final MethodHandle UNWRAP_FUNCTION;

    static {
        try {
            UNWRAP_OPTIONAL = LOOKUP.findStatic(FunctionHooks.class, "unwrapOptional", MethodType.methodType(Value.class, Value[].class, int.class));
            UNWRAP_VARARGS = LOOKUP.findStatic(FunctionHooks.class, "unwrapVarargs", MethodType.methodType(Value.class, Value[].class, int.class));
            UNWRAP_FUNCTION = LOOKUP.findStatic(FunctionHooks.class, "unwrapFunction", MethodType.methodType(PatchFunction.class, Value.class));

            for (var clazz : FunctionBody.class.getClasses()) {
                var i = Integer.parseInt(clazz.getSimpleName().substring(1));
                FUNCTION_BODY_CLASSES[i] = clazz;
                FUNCTION_INVOKERS[i] = LOOKUP.findVirtual(clazz, "call", makeType(i));
            }

            INSTANCEOF_CHECK = MethodHandles.permuteArguments(LOOKUP.findVirtual(Class.class, "isInstance",
                            MethodType.methodType(boolean.class, Object.class)),
                    MethodType.methodType(boolean.class, Object.class, Class.class), 1, 0);
            CALL_BUILTIN_FUNCTION = LOOKUP.findVirtual(PatchFunction.BuiltInPatchFunction.class, "execute", MethodType.methodType(Value.class, PlatformContext.class, List.class));
            ARRAY_AS_LIST = LOOKUP.findStatic(Arrays.class, "asList", MethodType.methodType(List.class, Object[].class));
            DEFINED_FUNCTION_HANDLE = LOOKUP.findVirtual(DefinedFunction.class, "handle", MethodType.methodType(MethodHandle.class));
            GET_WRONG_RUNTIME_ERROR = LOOKUP.findStatic(FunctionHooks.class, "getWrongRuntimeError", MethodType.methodType(IllegalStateException.class, PatchFunction.class));
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
    
    public static CallSite callHook(MethodHandles.Lookup caller,
                                    String methodName,
                                    MethodType methodType) {
        if (methodType.returnType() != Value.class) throw new IllegalArgumentException("Return type must be Value");
        if (methodType.parameterCount() == 0 || methodType.parameterType(0) != PlatformContext.class) throw new IllegalArgumentException("First argument must be EvaluationContext");
        if (methodType.parameterCount() == 1 || methodType.parameterType(1) != Value.class) throw new IllegalArgumentException("Second argument must be Value");
        if (Arrays.stream(methodType.parameterArray()).skip(1).anyMatch(argClass -> argClass != Value.class)) {
            throw new IllegalArgumentException("Argument types must be Value");
        }

        // TODO: The massive method handle only needs the arg count of the call, we can probably cache this

        // Helper types for the big method handle
        // The type of the function implementation handles
        var targetType = MethodType.methodType(Value.class, PlatformContext.class, PatchFunction.class, Value[].class);
        // The type of the instanceof handles
        var testType = MethodType.methodType(boolean.class, PlatformContext.class, PatchFunction.class, Value[].class);

        // We build this spaghetti method handle instead of using a normal method because this doesn't show up on stacktraces
        // It's a lot nicer for end users when it looks like their functions are directly calling each other
        /*
        This code roughly corresponds to:
        (context, function, args) -> {
            if (function instanceof PatchFunction.BuiltInPatchFunction) {
                return ((PatchFunction.BuiltInPatchFunction)functions).execute(context, Arrays.asList(args));
            } else {
                if (function instanceof DefinedFunction) {
                    return ((DefinedFunction)function).handle().invokeWithArguments(args);
                } else {
                    throw getWrongRuntimeError(function);
                }
            }
        }
         */
        var handler = MethodHandles.guardWithTest(
                // Check for builtin functions
                MethodHandles.permuteArguments(MethodHandles.insertArguments(INSTANCEOF_CHECK, 1, PatchFunction.BuiltInPatchFunction.class).asType(MethodType.methodType(boolean.class, PatchFunction.class)), testType, 1),
                // If builtin, first wrap args array into a list
                MethodHandles.filterArguments(
                        // Then call the execute method, shuffling some args around
                        MethodHandles.permuteArguments(CALL_BUILTIN_FUNCTION, MethodType.methodType(Value.class, PlatformContext.class, PatchFunction.BuiltInPatchFunction.class, List.class), 1, 0, 2),
                        2,
                        ARRAY_AS_LIST
                ).asType(targetType),
                // If not builtin
                MethodHandles.guardWithTest(
                        // Check for function from this runtime
                        MethodHandles.permuteArguments(MethodHandles.insertArguments(INSTANCEOF_CHECK, 1, DefinedFunction.class).asType(MethodType.methodType(boolean.class, PatchFunction.class)), testType, 1),
                        // If function from this runtime
                        MethodHandles.dropArguments( // Drop platform context as user functions don't need it
                                MethodHandles.filterArguments( // Extract function method handle
                                        // Actual function caller, some weird tricks involving the exact arg count used for the call
                                        MethodHandles.spreadInvoker(MethodType.methodType(Value.class, Stream.generate(() -> Value.class).limit(methodType.parameterCount() - 2).toArray(Class[]::new)), 0),
                                        0,
                                        DEFINED_FUNCTION_HANDLE
                                ),
                                0,
                                PlatformContext.class
                        ).asType(targetType),
                        // Else we have someone else's function -> throw informative error
                        MethodHandles.permuteArguments( // Drop all args except function
                                // Throw, but first wrap turn the function into an exception
                                MethodHandles.filterArguments(MethodHandles.throwException(Value.class, IllegalStateException.class), 0, GET_WRONG_RUNTIME_ERROR),
                                targetType,
                                1
                        )
                )
        );

        // Finally we wrap all that in another method handle that unwraps the function from a value, and then apply varargs correctly
        return new ConstantCallSite(MethodHandles.filterArguments(handler, 1, UNWRAP_FUNCTION).asVarargsCollector(Value[].class).asType(methodType));
    }

    private static IllegalStateException getWrongRuntimeError(PatchFunction function) {
        return new IllegalStateException("Tried to call function from another runtime: " + function);
    }
    
    public static Value call(PlatformContext context, PatchFunction function, Value... args) {
        return switch (function) {
            case PatchFunction.BuiltInPatchFunction builtIn -> builtIn.execute(context, Arrays.asList(args));
            case DefinedFunction definedFunction -> definedFunction.call(args);
            case PatchFunction.RuntimePatchFunction other -> throw  getWrongRuntimeError(other);
        };
    }
    
    private static PatchFunction unwrapFunction(Value value) {
        if (value instanceof Value.FunctionValue(var function)) return function;
        throw new IncompatibleOperandsException("%s is not callable".formatted(value));
    }
    
    private static @Nullable Value unwrapOptional(Value[] values, int index) {
        if (index < values.length) {
            return values[index];
        }
        return null;
    }
    
    private static Value unwrapVarargs(Value[] values, int optionalCount) {
        if (values.length <= optionalCount) return new Value.ArrayValue(Collections.emptyList());
        return new Value.ArrayValue(Arrays.asList(values).subList(optionalCount - 1, values.length));
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

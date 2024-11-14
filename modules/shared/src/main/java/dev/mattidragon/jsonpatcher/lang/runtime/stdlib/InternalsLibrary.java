package dev.mattidragon.jsonpatcher.lang.runtime.stdlib;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface InternalsLibrary {
    default void arrayInsert(Value.ArrayValue array, Value.NumberValue index, Value value) {
        array.value().add((int) index.value(), value);
    }
    
    void _throw(Value value);
    
    void log(Value value);
    
    default Value.ArrayValue keys(Value.ObjectValue value) {
        var array = new Value.ArrayValue();
        value.value().keySet().stream().map(Value.StringValue::new).forEach(array.value()::add);
        return array;
    }

    default void defineMethods(Value.StringValue type, Value.ObjectValue methods) {
        var actualType = switch (type.value()) {
            case "number" -> ValueType.NUMBER;
            case "string" -> ValueType.STRING;
            case "boolean" -> ValueType.BOOLEAN;
            case "array" -> ValueType.ARRAY;
            case "object" -> ValueType.OBJECT;
            case "null" -> ValueType.NULL;
            case "function" -> ValueType.FUNCTION;
            default -> throw new IllegalStateException("Unsupported type: " + type.value());
        };
        
        defineMethods(actualType, methods.value());
    }

    @DontBind
    void defineMethods(ValueType type, Map<String, Value> methods);
    
    default Value.FunctionValue bindMathFunction(Value.StringValue name) {
        try {
            var method = MethodHandles.lookup().findStatic(Math.class, name.value(), MethodType.methodType(double.class, double.class));
            return new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (context, args) -> {
                if (args.size() != 1) throw context.createException("Expected one argument to " + name.value() + " but got " + args);
                if (!(args.getFirst() instanceof Value.NumberValue(var number))) throw context.createException("Expected argument to " + name.value() + " to be number, but got " + args.getFirst());
                try {
                    return new Value.NumberValue((Double) method.invoke(number));
                } catch (Throwable e) {
                    throw new IllegalStateException("Failed to call math function", e);
                }
            });
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to bind math function: " + name, e);
        }
    }
    
    default Value.NumberValue e() {
        return new Value.NumberValue(Math.E);
    }
    
    default Value.NumberValue pi() {
        return new Value.NumberValue(Math.PI);
    }

    default Value.NumberValue max(Value.NumberValue first, Value.NumberValue second) {
        return new Value.NumberValue(Math.max(first.value(), second.value()));
    }

    default Value.NumberValue min(Value.NumberValue first, Value.NumberValue second) {
        return new Value.NumberValue(Math.min(first.value(), second.value()));
    }

    default Value.FunctionValue bind(Value.FunctionValue function, Value value, Value.NumberValue index) {
        return new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (context, args) -> {
            var newArgs = new ArrayList<>(args);
            newArgs.add((int) index.value(), value);
            return context.execute(function.function(), newArgs);
        });
    }

    default Value.FunctionValue then(Value.FunctionValue function, Value.FunctionValue next) {
        return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> {
            var result = context.execute(function.function(), args);
            return context.execute(next.function(), List.of(result));
        }));
    }

    default void loadStringLib(Value.ObjectValue object) {
        // Strings require tons of bindings into java code, so it's easier to just reuse the old stdlib
        new LibraryBuilder(Libraries.StringsLibrary.class, Method.class).build(object);
    }
}

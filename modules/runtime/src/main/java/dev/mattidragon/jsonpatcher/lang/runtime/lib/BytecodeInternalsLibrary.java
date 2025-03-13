package dev.mattidragon.jsonpatcher.lang.runtime.lib;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.runtime.PatchException;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.builder.DisableErrorWrapping;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.builder.DontBind;
import dev.mattidragon.jsonpatcher.lang.runtime.util.PropertyHolder;
import dev.mattidragon.jsonpatcher.lang.runtime.value.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class BytecodeInternalsLibrary {
    private final Consumer<Value> logConsumer;
    private final PropertyHolder propertyHolder;

    public BytecodeInternalsLibrary(Consumer<Value> logConsumer, PropertyHolder propertyHolder) {
        this.logConsumer = logConsumer;
        this.propertyHolder = propertyHolder;
    }

    @DisableErrorWrapping
    public void _throw(Value value) {
        throw new PatchException(value.toString());
    }

    public void log(Value value) {
        logConsumer.accept(value);
    }

    @DontBind
    public void defineMethods(ValueType type, Map<String, Value> methods) {
        propertyHolder.define(type, methods);
    }

    public void arrayInsert(Value.ArrayValue array, Value.NumberValue index, Value value) {
        array.value().add((int) index.value(), value);
    }

    public Value.ArrayValue keys(Value.ObjectValue value) {
        var array = new Value.ArrayValue();
        value.value().keySet().stream().map(Value.StringValue::new).forEach(array.value()::add);
        return array;
    }

    public void defineMethods(Value.StringValue type, Value.ObjectValue methods) {
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

    public Value.FunctionValue bindMathFunction(Value.StringValue name) {
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

    public Value.NumberValue e() {
        return new Value.NumberValue(Math.E);
    }

    public Value.NumberValue pi() {
        return new Value.NumberValue(Math.PI);
    }

    public Value.NumberValue max(Value.NumberValue first, Value.NumberValue second) {
        return new Value.NumberValue(Math.max(first.value(), second.value()));
    }

    public Value.NumberValue min(Value.NumberValue first, Value.NumberValue second) {
        return new Value.NumberValue(Math.min(first.value(), second.value()));
    }

    public Value.FunctionValue bind(Value.FunctionValue function, Value value, Value.NumberValue index) {
        return new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (context, args) -> {
            var newArgs = new ArrayList<>(args);
            newArgs.add((int) index.value(), value);
            return context.execute(function.function(), newArgs);
        });
    }

    public Value.FunctionValue then(Value.FunctionValue function, Value.FunctionValue next) {
        return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> {
            var result = context.execute(function.function(), args);
            return context.execute(next.function(), List.of(result));
        }));
    }

    public Value freeze(Value value) {
        return switch (value) {
            case Value.ObjectValue(var map, var frozen) -> new Value.ObjectValue(map, true);
            case Value.ArrayValue(var elements, var frozen) -> new Value.ArrayValue(elements, true);
            // Functions and primitives are already immutable, so we can just return them.
            case Value.FunctionValue function -> function;
            case Value.Primitive primitive -> primitive;
            case Value.SpecialValue specialValue -> specialValue.freeze();
        };
    }
}

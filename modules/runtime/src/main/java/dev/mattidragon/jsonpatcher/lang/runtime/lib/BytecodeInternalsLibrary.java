package dev.mattidragon.jsonpatcher.lang.runtime.lib;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.PatchException;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.builder.DisableErrorWrapping;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.builder.DontBind;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.JavaObjectValue;
import dev.mattidragon.jsonpatcher.lang.runtime.util.PropertyHolder;
import dev.mattidragon.jsonpatcher.lang.runtime.value.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class BytecodeInternalsLibrary {
    private final Consumer<Value> logConsumer;
    private final PropertyHolder propertyHolder;

    public final JavaObjectValue fakeEvalCtx;

    public BytecodeInternalsLibrary(Consumer<Value> logConsumer, PropertyHolder propertyHolder) {
        this.logConsumer = logConsumer;
        this.propertyHolder = propertyHolder;
        this.fakeEvalCtx = new JavaObjectValue(new EvaluationContext(this.propertyHolder, name -> {
            throw new UnsupportedOperationException("Fake eval env does not have libs");
        }));
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

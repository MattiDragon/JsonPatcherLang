package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.List;

public record PropertyAccessExpression(Expression parent, String name, SourceSpan pos, SourceSpan namePos) implements Reference {
    @Override
    public Value get(EvaluationContext context) {
        var parent = this.parent.evaluate(context);

        switch (parent) {
            case Value.ObjectValue objectValue -> {
                return objectValue.get(name, context.config(), pos);
            }
            case Value.ArrayValue arrayValue -> {
                if (name.equals("length")) {
                    return new Value.NumberValue(arrayValue.value().size());
                } else if (Libraries.ArraysLibrary.METHODS.containsKey(name)) {
                    var function = Libraries.ArraysLibrary.METHODS.get(name);
                    return new Value.FunctionValue(function.bind(arrayValue));
                } else {
                    throw error(context, "Tried to read invalid property %s of %s.".formatted(name, parent));
                }
            }
            case Value.StringValue stringValue -> {
                if (Libraries.StringsLibrary.METHODS.containsKey(name)) {
                    var function = Libraries.StringsLibrary.METHODS.get(name);
                    return new Value.FunctionValue(function.bind(stringValue));
                } else {
                    throw error(context, "Tried to read invalid property %s of %s.".formatted(name, parent));
                }
            }
            case Value.FunctionValue functionValue -> {
                if (Libraries.FunctionsLibrary.METHODS.containsKey(name)) {
                    var function = Libraries.FunctionsLibrary.METHODS.get(name);
                    return new Value.FunctionValue(function.bind(functionValue));
                } else {
                    throw error(context, "Tried to read invalid property %s of %s.".formatted(name, parent));
                }
            }
            default -> throw error(context, "Tried to read property %s of %s. Only objects and arrays have properties.".formatted(name, parent));
        }
    }

    @Override
    public void set(EvaluationContext context, Value value) {
        var parent = this.parent.evaluate(context);
        if (parent instanceof Value.ObjectValue objectValue) {
            objectValue.set(name, value, context.config(), pos);
        } else {
            throw error(context, "Tried to write property %s of %s. Only objects have properties.".formatted(name, parent));
        }
    }

    @Override
    public void delete(EvaluationContext context) {
        var parent = this.parent.evaluate(context);
        if (parent instanceof Value.ObjectValue objectValue) {
            objectValue.remove(name, context.config(), pos);
        } else {
            throw error(context, "Tried to delete property %s of %s. Only objects have properties.".formatted(name, parent));
        }
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(parent);
    }
}

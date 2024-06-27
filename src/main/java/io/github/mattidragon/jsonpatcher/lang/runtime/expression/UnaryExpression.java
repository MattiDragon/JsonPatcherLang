package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record UnaryExpression(Expression input, Operator op, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return op.apply(input.evaluate(context), pos, context.config());
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(input);
    }

    public interface Operator {
        Value apply(Value value, SourceSpan pos, LangConfig config);

        Operator NOT = (value, pos, config) -> {
            if (value instanceof Value.BooleanValue booleanValue) return Value.BooleanValue.of(!booleanValue.value());
            throw new EvaluationException(config, "Can't apply boolean not to %s. Only booleans are supported.".formatted(value), pos);
        };
        Operator MINUS = (value, pos, config) -> {
            if (value instanceof Value.NumberValue numberValue) return new Value.NumberValue(-numberValue.value());
            throw new EvaluationException(config, "Can't negate %s. Only numbers are supported.".formatted(value), pos);
        };
        Operator BITWISE_NOT = (value, pos, config) -> {
            if (value instanceof Value.NumberValue numberValue) return new Value.NumberValue(~(int) numberValue.value());
            throw new EvaluationException(config, "Can't apply bitwise not to %s. Only numbers are supported.".formatted(value), pos);
        };
        Operator INCREMENT = (value, pos, config) -> {
            if (value instanceof Value.NumberValue numberValue) return new Value.NumberValue(numberValue.value() + 1);
            throw new EvaluationException(config, "Can't negate %s. Only numbers are supported.".formatted(value), pos);
        };
        Operator DECREMENT = (value, pos, config) -> {
            if (value instanceof Value.NumberValue numberValue) return new Value.NumberValue(numberValue.value() - 1);
            throw new EvaluationException(config, "Can't negate %s. Only numbers are supported.".formatted(value), pos);
        };
    }
}

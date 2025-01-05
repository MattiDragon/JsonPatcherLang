package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.UnaryExpression;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

public class UnaryExpressionInterpreter {
    public static Value evaluate(UnaryExpression.Operator operator, Value value, @Nullable SourceSpan pos, EvaluationContext context) {
        return switch (operator) {
            case NOT -> {
                if (value instanceof Value.BooleanValue booleanValue)
                    yield Value.BooleanValue.of(!booleanValue.value());
                throw new EvaluationException(context.config(), "Can't apply boolean not to %s. Only booleans are supported.".formatted(value), pos);
            }
            case MINUS -> {
                if (value instanceof Value.NumberValue(var number))
                    yield new Value.NumberValue(-number);
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
            case BITWISE_NOT -> {
                if (value instanceof Value.NumberValue(var number))
                    yield new Value.NumberValue(~(int) number);
                throw new EvaluationException(context.config(), "Can't apply bitwise not to %s. Only numbers are supported.".formatted(value), pos);
            }
            case INCREMENT -> {
                if (value instanceof Value.NumberValue(var number))
                    yield new Value.NumberValue(number + 1);
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
            case DECREMENT -> {
                if (value instanceof Value.NumberValue(var number))
                    yield new Value.NumberValue(number - 1);
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
        };
    }
}

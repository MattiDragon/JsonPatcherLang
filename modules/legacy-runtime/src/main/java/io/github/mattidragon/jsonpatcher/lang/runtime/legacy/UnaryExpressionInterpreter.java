package io.github.mattidragon.jsonpatcher.lang.runtime.legacy;

import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.UnaryExpression;

public class UnaryExpressionInterpreter {
    public static Value evaluate(UnaryExpression.Operator operator, Value value, SourceSpan pos, EvaluationContext context) {
        return switch (operator) {
            case NOT -> {
                if (value instanceof Value.BooleanValue booleanValue)
                    yield Value.BooleanValue.of(!booleanValue.value());
                throw new EvaluationException(context.config(), "Can't apply boolean not to %s. Only booleans are supported.".formatted(value), pos);
            }
            case MINUS -> {
                if (value instanceof Value.NumberValue numberValue) 
                    yield new Value.NumberValue(-numberValue.value());
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
            case BITWISE_NOT -> {
                if (value instanceof Value.NumberValue numberValue)
                    yield new Value.NumberValue(~(int) numberValue.value());
                throw new EvaluationException(context.config(), "Can't apply bitwise not to %s. Only numbers are supported.".formatted(value), pos);
            }
            case INCREMENT -> {
                if (value instanceof Value.NumberValue numberValue)
                    yield new Value.NumberValue(numberValue.value() + 1);
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
            case DECREMENT -> {
                if (value instanceof Value.NumberValue numberValue)
                    yield new Value.NumberValue(numberValue.value() - 1);
                throw new EvaluationException(context.config(), "Can't negate %s. Only numbers are supported.".formatted(value), pos);
            }
        };
    }
}

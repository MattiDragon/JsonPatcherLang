package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;

public class BinaryOperatorInterpreter {
    public static Value evaluate(BinaryExpression.Operator operator, Value first, Value second, SourceSpan pos, EvaluationContext context) {
        var pair = new Pair(first, second);

        return switch (operator) {
            case PLUS ->  plus(pair, pos, context);
            case MINUS ->  minus(pair, pos, context);
            case MULTIPLY -> multiply(first, second, pos, context);
            case DIVIDE -> divide(first, second, pos, context);
            case MODULO -> modulo(first, second, pos, context);
            case EXPONENT -> exponent(first, second, pos, context);
            case AND -> and(pair, pos, context);
            case OR -> or(pair, pos, context);
            case XOR -> xor(pair, pos, context);
            case EQUALS -> Value.BooleanValue.of(Value.isEqual(first, second));
            case NOT_EQUALS -> Value.BooleanValue.of(!Value.isEqual(first, second));
            case LESS_THAN -> Value.BooleanValue.of(compare(pair, pos, context) < 0);
            case GREATER_THAN -> Value.BooleanValue.of(compare(pair, pos, context) > 0);
            case LESS_THAN_EQUAL -> Value.BooleanValue.of(compare(pair, pos, context) <= 0);
            case GREATER_THAN_EQUAL -> Value.BooleanValue.of(compare(pair, pos, context) >= 0);
            case IN -> in(first, second, pos, context);
            case ASSIGN -> second;
        };
    }

    private static Value plus(Pair pair, SourceSpan pos, EvaluationContext context) {
        return switch (pair) {
            case Pair(Value.NumberValue(var first), Value.NumberValue(var second)) -> new Value.NumberValue(first + second);
            case Pair(Value.StringValue(var first), Value.StringValue(var second)) -> new Value.StringValue(first + second);
            case Pair(Value.ArrayValue(var first), Value.ArrayValue(var second)) -> {
                var array = new Value.ArrayValue();
                array.value().addAll(first);
                array.value().addAll(second);
                yield array;
            }
            case Pair(Value.ObjectValue(var first), Value.ObjectValue(var second)) -> {
                var object = new Value.ObjectValue();
                object.value().putAll(first);
                object.value().putAll(second);
                yield object;
            }
            default -> throw new EvaluationException(context.config(), "Can't add %s and %s together".formatted(pair.first, pair.second), pos);
        };
    }

    private static Value minus(Pair pair, SourceSpan pos, EvaluationContext context) {
        if (pair instanceof Pair(Value.NumberValue first, Value.NumberValue second)) {
            return new Value.NumberValue(first.value() - second.value());
        }
        throw new EvaluationException(context.config(), "Can't subtract %s from %s".formatted(pair.first, pair.second), pos);
    }

    private static Value multiply(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        if (!(second instanceof Value.NumberValue(var multiplier))) {
            throw new EvaluationException(context.config(), "Can't multiply by %s".formatted(second), pos);
        }
        return switch (first) {
            case Value.NumberValue(var number) -> new Value.NumberValue(number * multiplier);
            case Value.StringValue(var string) -> new Value.StringValue(string.repeat((int) multiplier));
            case Value.ArrayValue(var values) ->  {
                var array = new Value.ArrayValue();
                for (int i = 0; i < (int) multiplier; i++) {
                    array.value().addAll(values);
                }
                yield array;
            }
            default -> throw new EvaluationException(context.config(), "Can't multiply %s with %s".formatted(first, second), pos);
        };
    }

    private static Value divide(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(number1 / number2);
        }
        throw new EvaluationException(context.config(), "Can't divide %s by %s".formatted(first, second), pos);
    }

    private static Value modulo(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(number1 % number2);
        }
        throw new EvaluationException(context.config(), "Can't take %s modulo %s".formatted(first, second), pos);
    }

    private static Value exponent(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(Math.pow(number1, number2));
        }
        throw new EvaluationException(context.config(), "Can't take %s to the %s".formatted(first, second), pos);
    }

    private static Value and(Pair pair, SourceSpan pos, EvaluationContext context) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 & (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() && boolean2.value());
            default -> throw new EvaluationException(context.config(), "Can't apply and to %s and %s".formatted(pair.first, pair.second), pos);
        };
    }

    private static Value or(Pair pair, SourceSpan pos, EvaluationContext context) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 | (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() || boolean2.value());
            default -> throw new EvaluationException(context.config(), "Can't apply and to %s and %s".formatted(pair.first, pair.second), pos);
        };
    }

    private static Value xor(Pair pair, SourceSpan pos, EvaluationContext context) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 ^ (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() ^ boolean2.value());
            default -> throw new EvaluationException(context.config(), "Can't apply and to %s and %s".formatted(pair.first, pair.second), pos);
        };
    }

    private static Value in(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        if (second instanceof Value.ArrayValue arrayValue) {
            return Value.BooleanValue.of(arrayValue.value().contains(first));
        }
        if (first instanceof Value.StringValue string && second instanceof Value.ObjectValue objectValue) {
            return Value.BooleanValue.of(objectValue.value().containsKey(string.value()));
        }
        throw new EvaluationException(context.config(), "Can't check if %s is in %s".formatted(first, second), pos);
    }

    private static int compare(Pair pair, SourceSpan pos, EvaluationContext context) {
        if (pair instanceof Pair(Value.NumberValue(var first), Value.NumberValue(var second))) {
            return Double.compare(first, second);
        }
        throw new EvaluationException(context.config(), "Can't compare %s and %s".formatted(pair.first, pair.second), pos);
    }

    private record Pair(Value first, Value second) {}
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;

import java.lang.invoke.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class BinaryExpressionHooks {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType BOOTSTRAP_TYPE = MethodType.methodType(Value.class, Value.class, Value.class, SourceSpan.class, EvaluationContext.class);
    private static final MethodHandle PAIR_CONSTRUCTOR;
    private static final Map<BinaryExpression.Operator, MethodHandle> OPERATOR_HANDLES;

    static {
        try {
            PAIR_CONSTRUCTOR = LOOKUP.findConstructor(Pair.class, MethodType.methodType(void.class, Value.class, Value.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to find internal method handle", e);
        }
        var map = new EnumMap<BinaryExpression.Operator, MethodHandle>(BinaryExpression.Operator.class);
        map.put(BinaryExpression.Operator.PLUS, getPacked("plus"));
        map.put(BinaryExpression.Operator.MINUS, getPacked("minus"));
        map.put(BinaryExpression.Operator.MULTIPLY, getLoose("multiply"));
        map.put(BinaryExpression.Operator.DIVIDE, getLoose("divide"));
        map.put(BinaryExpression.Operator.MODULO, getLoose("modulo"));
        map.put(BinaryExpression.Operator.EXPONENT, getLoose("exponent"));
        map.put(BinaryExpression.Operator.AND, getPacked("and"));
        map.put(BinaryExpression.Operator.OR, getPacked("or"));
        map.put(BinaryExpression.Operator.XOR, getPacked("xor"));
        map.put(BinaryExpression.Operator.EQUALS, getLoose("equal"));
        map.put(BinaryExpression.Operator.NOT_EQUALS, getLoose("notEqual"));
        map.put(BinaryExpression.Operator.LESS_THAN, getPacked("lessThan"));
        map.put(BinaryExpression.Operator.GREATER_THAN, getPacked("greaterThan"));
        map.put(BinaryExpression.Operator.LESS_THAN_EQUAL, getPacked("lessThanEqual"));
        map.put(BinaryExpression.Operator.GREATER_THAN_EQUAL, getPacked("greaterThanEqual"));
        map.put(BinaryExpression.Operator.IN, getLoose("in"));
        map.put(BinaryExpression.Operator.ASSIGN, MethodHandles.dropArguments(MethodHandles.dropArguments(MethodHandles.identity(Value.class), 1, SourceSpan.class, EvaluationContext.class), 0, Value.class));
        OPERATOR_HANDLES = Collections.unmodifiableMap(map);
    }
    
    private static MethodHandle getLoose(String name) {
        try {
            return LOOKUP.findStatic(BinaryExpressionHooks.class, name, MethodType.methodType(Value.class, Value.class, Value.class, SourceSpan.class, EvaluationContext.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to find internal method handle", e);
        }
    }
    
    private static MethodHandle getPacked(String name) {
        try {
            var raw = LOOKUP.findStatic(BinaryExpressionHooks.class, name, MethodType.methodType(Value.class, Pair.class, SourceSpan.class, EvaluationContext.class));
            return MethodHandles.collectArguments(raw, 0, PAIR_CONSTRUCTOR);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to find internal method handle", e);
        }
    }

    public static CallSite hook(MethodHandles.Lookup caller,
                                String methodName,
                                MethodType methodType) {
        if (!methodType.equals(BOOTSTRAP_TYPE)) {
            throw new IllegalStateException("Incorrect signature expected from bootstrap method: " + methodType);
        }

        return new ConstantCallSite(OPERATOR_HANDLES.get(BinaryExpression.Operator.valueOf(methodName.toUpperCase(Locale.ROOT))));
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
    
    private static Value lessThan(Pair pair, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(compare(pair, pos, context) < 0);
    }
    
    private static Value greaterThan(Pair pair, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(compare(pair, pos, context) > 0);
    }
    
    private static Value lessThanEqual(Pair pair, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(compare(pair, pos, context) <= 0);
    }
    
    private static Value greaterThanEqual(Pair pair, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(compare(pair, pos, context) >= 0);
    }
    
    private static Value equal(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(Value.isEqual(first, second));
    }
    
    private static Value notEqual(Value first, Value second, SourceSpan pos, EvaluationContext context) {
        return Value.BooleanValue.of(!Value.isEqual(first, second));
    }

    private record Pair(Value first, Value second) {}
}

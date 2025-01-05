package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.IncompatibleOperandsException;

import java.lang.invoke.*;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
public class BinaryExpressionHooks {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodType BOOTSTRAP_TYPE = MethodType.methodType(Value.class, Value.class, Value.class);
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
        map.put(BinaryExpression.Operator.ASSIGN, MethodHandles.dropArguments(MethodHandles.identity(Value.class), 0, Value.class));
        OPERATOR_HANDLES = Collections.unmodifiableMap(map);
    }
    
    private static MethodHandle getLoose(String name) {
        try {
            return LOOKUP.findStatic(BinaryExpressionHooks.class, name, MethodType.methodType(Value.class, Value.class, Value.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unable to find internal method handle", e);
        }
    }
    
    private static MethodHandle getPacked(String name) {
        try {
            var raw = LOOKUP.findStatic(BinaryExpressionHooks.class, name, MethodType.methodType(Value.class, Pair.class));
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
    
    private static Value plus(Pair pair) {
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
            default -> throw new IncompatibleOperandsException("Can't add %s and %s together".formatted(pair.first, pair.second));
        };
    }

    private static Value minus(Pair pair) {
        if (pair instanceof Pair(Value.NumberValue first, Value.NumberValue second)) {
            return new Value.NumberValue(first.value() - second.value());
        }
        throw new IncompatibleOperandsException("Can't subtract %s from %s".formatted(pair.first, pair.second));
    }

    private static Value multiply(Value first, Value second) {
        if (!(second instanceof Value.NumberValue(var multiplier))) {
            throw new IncompatibleOperandsException("Can't multiply by %s".formatted(second));
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
            default -> throw new IncompatibleOperandsException("Can't multiply %s with %s".formatted(first, second));
        };
    }

    private static Value divide(Value first, Value second) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(number1 / number2);
        }
        throw new IncompatibleOperandsException("Can't divide %s by %s".formatted(first, second));
    }

    private static Value modulo(Value first, Value second) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(number1 % number2);
        }
        throw new IncompatibleOperandsException("Can't take %s modulo %s".formatted(first, second));
    }

    private static Value exponent(Value first, Value second) {
        if (first instanceof Value.NumberValue(var number1) && second instanceof Value.NumberValue(var number2)) {
            return new Value.NumberValue(Math.pow(number1, number2));
        }
        throw new IncompatibleOperandsException("Can't take %s to the %s".formatted(first, second));
    }

    private static Value and(Pair pair) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 & (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() && boolean2.value());
            default -> throw new IncompatibleOperandsException("Can't apply and to %s and %s".formatted(pair.first, pair.second));
        };
    }

    private static Value or(Pair pair) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 | (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() || boolean2.value());
            default -> throw new IncompatibleOperandsException("Can't apply or to %s and %s".formatted(pair.first, pair.second));
        };
    }

    private static Value xor(Pair pair) {
        return switch (pair) {
            case Pair(Value.NumberValue(var number1), Value.NumberValue(var number2)) -> new Value.NumberValue((int) number1 ^ (int) number2);
            case Pair(Value.BooleanValue boolean1, Value.BooleanValue boolean2) -> Value.BooleanValue.of(boolean1.value() ^ boolean2.value());
            default -> throw new IncompatibleOperandsException("Can't apply xor to %s and %s".formatted(pair.first, pair.second));
        };
    }

    private static Value in(Value first, Value second) {
        if (second instanceof Value.ArrayValue(var array)) {
            return Value.BooleanValue.of(array.contains(first));
        }
        if (first instanceof Value.StringValue(var key) && second instanceof Value.ObjectValue(var object)) {
            return Value.BooleanValue.of(object.containsKey(key));
        }
        throw new IncompatibleOperandsException("Can't check if %s is in %s".formatted(first, second));
    }

    private static int compare(Pair pair) {
        if (pair instanceof Pair(Value.NumberValue(var first), Value.NumberValue(var second))) {
            return Double.compare(first, second);
        }
        throw new IncompatibleOperandsException("Can't compare %s and %s".formatted(pair.first, pair.second));
    }
    
    private static Value lessThan(Pair pair) {
        return Value.BooleanValue.of(compare(pair) < 0);
    }
    
    private static Value greaterThan(Pair pair) {
        return Value.BooleanValue.of(compare(pair) > 0);
    }
    
    private static Value lessThanEqual(Pair pair) {
        return Value.BooleanValue.of(compare(pair) <= 0);
    }
    
    private static Value greaterThanEqual(Pair pair) {
        return Value.BooleanValue.of(compare(pair) >= 0);
    }
    
    private static Value equal(Value first, Value second) {
        return Value.BooleanValue.of(Value.isEqual(first, second));
    }
    
    private static Value notEqual(Value first, Value second) {
        return Value.BooleanValue.of(!Value.isEqual(first, second));
    }

    private record Pair(Value first, Value second) {}
}

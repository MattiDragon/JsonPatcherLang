package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;
import java.util.function.BiPredicate;

public record BinaryExpression(Expression first, Expression second, Operator op, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return op.apply(first.evaluate(context), second.evaluate(context), pos, context.config());
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(first, second);
    }

    public interface Operator {
        Value apply(Value first, Value second, SourceSpan pos, LangConfig config);

        Operator PLUS = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue(number1.value() + number2.value());
            }
            if (first instanceof Value.StringValue string1
                && second instanceof Value.StringValue string2) {
                return new Value.StringValue(string1.value() + string2.value());
            }
            if (first instanceof Value.ArrayValue array1
                && second instanceof Value.ArrayValue array2) {
                var array = new Value.ArrayValue();
                array.value().addAll(array1.value());
                array.value().addAll(array2.value());
                return array;
            }
            if (first instanceof Value.ObjectValue object1
                && second instanceof Value.ObjectValue object2) {
                var object = new Value.ObjectValue();
                object.value().putAll(object1.value());
                object.value().putAll(object2.value());
                return (object);
            }
            throw new EvaluationException(config, "Can't add %s and %s together".formatted(first, second), pos);
        };
        Operator MINUS = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue(number1.value() - number2.value());
            }
            throw new EvaluationException(config, "Can't subtract %s from %s".formatted(second, first), pos);
        };
        Operator MULTIPLY = (first, second, pos, config) -> {
            if (!(second instanceof Value.NumberValue number2)) {
                throw new EvaluationException(config, "Can't multiply by %s".formatted(second), pos);
            }
            if (first instanceof Value.NumberValue number1) {
                return new Value.NumberValue(number1.value() * number2.value());
            }
            if (first instanceof Value.StringValue string1) {
                return new Value.StringValue(string1.value().repeat((int) number2.value()));
            }
            if (first instanceof Value.ArrayValue array1) {
                var array = new Value.ArrayValue();
                for (int i = 0; i < (int) number2.value(); i++) {
                    array.value().addAll(array1.value());
                }
                return array;
            }
            throw new EvaluationException(config, "Can't multiply %s with %s".formatted(first, second), pos);
        };
        Operator DIVIDE = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue(number1.value() / number2.value());
            }
            throw new EvaluationException(config, "Can't divide %s by %s".formatted(first, second), pos);
        };
        Operator MODULO = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue(number1.value() % number2.value());
            }
            throw new EvaluationException(config, "Can't take %s modulo %s".formatted(first, second), pos);
        };
        Operator EXPONENT = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue(Math.pow(number1.value(), number2.value()));
            }
            throw new EvaluationException(config, "Can't take %s to the %s".formatted(first, second), pos);
        };

        Operator AND = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue((int) number1.value() & (int) number2.value());
            }
            if (first instanceof Value.BooleanValue boolean1
                && second instanceof Value.BooleanValue boolean2) {
                return Value.BooleanValue.of(boolean1.value() && boolean2.value());
            }
            throw new EvaluationException(config, "Can't apply and to %s and %s".formatted(first, second), pos);
        };
        Operator OR = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue((int) number1.value() | (int) number2.value());
            }
            if (first instanceof Value.BooleanValue boolean1
                && second instanceof Value.BooleanValue boolean2) {
                return Value.BooleanValue.of(boolean1.value() || boolean2.value());
            }
            throw new EvaluationException(config, "Can't apply or to %s and %s".formatted(first, second), pos);
        };
        Operator XOR = (first, second, pos, config) -> {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return new Value.NumberValue((int) number1.value() ^ (int) number2.value());
            }
            if (first instanceof Value.BooleanValue boolean1
                && second instanceof Value.BooleanValue boolean2) {
                return Value.BooleanValue.of(boolean1.value() ^ boolean2.value());
            }
            throw new EvaluationException(config, "Can't apply xor to %s and %s".formatted(first, second), pos);
        };

        Operator EQUALS = (first, second, pos, config) -> Value.BooleanValue.of(isEqual(first, second));
        Operator NOT_EQUALS = (first, second, pos, config) -> Value.BooleanValue.of(!isEqual(first, second));
        Operator LESS_THAN = numberComparison((first, second) -> first < second);
        Operator GREATER_THAN = numberComparison((first, second) -> first > second);
        Operator LESS_THAN_EQUAL = numberComparison((first, second) -> first <= second);
        Operator GREATER_THAN_EQUAL = numberComparison((first, second) -> first >= second);

        Operator IN = (first, second, pos, config) -> {
            if (second instanceof Value.ArrayValue arrayValue) {
                return Value.BooleanValue.of(arrayValue.value().contains(first));
            }
            if (first instanceof Value.StringValue string && second instanceof Value.ObjectValue objectValue) {
                return Value.BooleanValue.of(objectValue.value().containsKey(string.value()));
            }
            throw new EvaluationException(config, "Can't check if %s is in %s".formatted(first, second), pos);
        };

        /**
         * Special operator used for the normal assignment. Returns the second value.
         * @implNote This operator is checked for with an identity check. An equivalent operator will not work.
         */
        Operator ASSIGN = (first, second, pos, config) -> second;

        private static boolean isEqual(Value first, Value second) {
            if (first instanceof Value.NumberValue number1
                && second instanceof Value.NumberValue number2) {
                return number1.value() == number2.value();
            }
            if (first instanceof Value.StringValue string1
                && second instanceof Value.StringValue string2) {
                return string1.value().equals(string2.value());
            }
            if (first instanceof Value.BooleanValue boolean1
                && second instanceof Value.BooleanValue boolean2) {
                return boolean1.value() == boolean2.value();
            }
            if (first instanceof Value.ArrayValue array1
                && second instanceof Value.ArrayValue array2) {
                return array1.value().equals(array2.value());
            }
            if (first instanceof Value.ObjectValue object1
                && second instanceof Value.ObjectValue object2) {
                return object1.value().equals(object2.value());
            }
            if (first instanceof Value.FunctionValue function1
                && second instanceof Value.FunctionValue function2) {
                return function1 == function2;
            }
            return first == Value.NullValue.NULL && second == Value.NullValue.NULL;
        }

        private static Operator numberComparison(BiPredicate<Double, Double> predicate) {
            return (first, second, pos, config) -> {
                if (first instanceof Value.NumberValue number1
                    && second instanceof Value.NumberValue number2) {
                    return Value.BooleanValue.of(predicate.test(number1.value(), number2.value()));
                }
                throw new EvaluationException(config, "Can't compare %s and %s".formatted(second, first), pos);
            };
        }
    }
}

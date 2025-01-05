package dev.mattidragon.jsonpatcher.lang.analysis.constant;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

/**
 * A simple analyser that finds and marks constant expressions in the AST.
 */
public class ConstantAnalyser {
    public static final MetadataKey<Value.Primitive> CONSTANT_VALUE = new MetadataKey<>("ConstantAnalyser/CONSTANT_VALUE");

    private final TreeMetadata metadata;

    private ConstantAnalyser(TreeMetadata metadata) {
        this.metadata = metadata;
    }

    // TODO: Actually use this
    public static void analyse(Program program, TreeMetadata metadata) {
        new ConstantAnalyser(metadata).analyse(program);
    }

    private void analyse(ProgramNode node) {
        if (node instanceof Expression expr) {
            analyseExpression(expr);
        } else {
            node.getChildren().forEach(this::analyse);
        }
    }

    private Value.@Nullable Primitive analyseExpression(Expression expr) {
        var value = switch (expr) {
            case ValueExpression(var exprValue) -> exprValue;
            case UnaryExpression(var input, var op) -> {
                var inputValue = analyseExpression(input);
                if (inputValue == null) yield null;
                yield computeUnary(op, inputValue);
            }
            case BinaryExpression(var first, var second, var op) -> {
                var firstValue = analyseExpression(first);
                var secondValue = analyseExpression(second);
                if (firstValue == null || secondValue == null) yield null;
                yield computeBinary(op, firstValue, secondValue);
            }
            case ShortedBinaryExpression(var first, var second, var op) -> {
                var firstValue = analyseExpression(first);
                var secondValue = analyseExpression(second);
                // If we short after the first value, the second expr never runs, and can thus be non-constant
                if (firstValue == null) yield null;
                yield switch (op) {
                    case AND -> firstValue.asBoolean() ? secondValue : firstValue;
                    case OR -> firstValue.asBoolean() ? firstValue : secondValue;
                };
            }
            case TernaryExpression(var condition, var ifTrue, var ifFalse) -> {
                var conditionValue = analyseExpression(condition);
                var trueValue = analyseExpression(ifTrue);
                var falseValue = analyseExpression(ifFalse);
                // If the condition is constant, we don't care about that branch that's never taken
                if (conditionValue == null) yield null;
                yield conditionValue.asBoolean() ? trueValue : falseValue;
            }
            default -> {
                expr.getChildren().forEach(this::analyse);
                yield null;
            }
        };
        if (value != null) metadata.put(expr, CONSTANT_VALUE, value);
        return value;
    }

    private Value.@Nullable Primitive computeBinary(BinaryExpression.Operator op, Value.Primitive first, Value.Primitive second) {
        record Pair(Value.Primitive first, Value.Primitive second) {}
        var pair = new Pair(first, second);

        return switch (op) {
            case PLUS -> switch (pair) {
                case Pair(Value.NumberValue(var a), Value.NumberValue(var b)) -> new Value.NumberValue(a + b);
                case Pair(Value.StringValue(var a), Value.StringValue(var b)) -> new Value.StringValue(a + b);
                default -> null;
            };
            case MINUS -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? new Value.NumberValue(a - b) : null;
            case MULTIPLY -> switch (pair) {
                case Pair(Value.NumberValue(var a), Value.NumberValue(var b)) -> new Value.NumberValue(a * b);
                case Pair(Value.StringValue(var a), Value.NumberValue(var b)) -> new Value.StringValue(a.repeat((int) b));
                default -> null;
            };
            case DIVIDE -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? new Value.NumberValue(a / b) : null;
            case MODULO -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? new Value.NumberValue(a % b) : null;
            case EXPONENT -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? new Value.NumberValue(Math.pow(a, b)) : null;
            case AND -> switch (pair) {
                case Pair(Value.NumberValue(var a), Value.NumberValue(var b)) -> new Value.NumberValue((int) a & (int) b);
                case Pair(Value.BooleanValue a, Value.BooleanValue b) -> Value.BooleanValue.of(a.value() && b.value());
                default -> null;
            };
            case OR -> switch (pair) {
                case Pair(Value.NumberValue(var a), Value.NumberValue(var b)) -> new Value.NumberValue((int) a | (int) b);
                case Pair(Value.BooleanValue a, Value.BooleanValue b) -> Value.BooleanValue.of(a.value() || b.value());
                default -> null;
            };
            case XOR -> switch (pair) {
                case Pair(Value.NumberValue(var a), Value.NumberValue(var b)) -> new Value.NumberValue((int) a ^ (int) b);
                case Pair(Value.BooleanValue a, Value.BooleanValue b) -> Value.BooleanValue.of(a.value() ^ b.value());
                default -> null;
            };
            case EQUALS -> Value.BooleanValue.of(first.equals(second));
            case NOT_EQUALS -> Value.BooleanValue.of(!first.equals(second));
            case LESS_THAN -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? Value.BooleanValue.of(a < b) : null;
            case GREATER_THAN -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? Value.BooleanValue.of(a > b) : null;
            case LESS_THAN_EQUAL -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? Value.BooleanValue.of(a <= b) : null;
            case GREATER_THAN_EQUAL -> pair instanceof Pair(Value.NumberValue(var a), Value.NumberValue(var b))
                    ? Value.BooleanValue.of(a >= b) : null;
            case IN, ASSIGN -> null;
        };
    }

    private static Value.@Nullable Primitive computeUnary(UnaryExpression.Operator op, Value.Primitive value) {
        return switch (op) {
            case NOT -> value instanceof Value.BooleanValue booleanValue
                    ? Value.BooleanValue.of(!booleanValue.value()) : null;
            case MINUS -> value instanceof Value.NumberValue(var numberValue)
                    ? new Value.NumberValue(-numberValue) : null;
            case BITWISE_NOT -> value instanceof Value.NumberValue(var numberValue)
                    ? new Value.NumberValue(~(int) numberValue) : null;
            default -> null;
        };
    }
}

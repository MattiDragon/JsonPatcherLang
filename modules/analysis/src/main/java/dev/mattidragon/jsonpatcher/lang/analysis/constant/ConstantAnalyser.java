package dev.mattidragon.jsonpatcher.lang.analysis.constant;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import org.jspecify.annotations.Nullable;

/**
 * A simple analyser that finds and marks constant expressions in the AST.
 */
public class ConstantAnalyser {
    public static final MetadataKey<ConstantValue> CONSTANT_VALUE = new MetadataKey<>("ConstantAnalyser/CONSTANT_ConstantValue");

    private final TreeMetadata metadata;

    private ConstantAnalyser(TreeMetadata metadata) {
        this.metadata = metadata;
    }

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

    private @Nullable ConstantValue analyseExpression(Expression expr) {
        var value = switch (expr) {
            case NumberExpression(var number) -> new ConstantValue.Number(number);
            case StringExpression(var string) -> new ConstantValue.String(string);
            case BooleanExpression(var bool) -> ConstantValue.Boolean.of(bool);
            case NullExpression() -> ConstantValue.Null.NULL;
            case UnaryExpression(var input, var op) -> {
                var inputConstantValue = analyseExpression(input);
                if (inputConstantValue == null) yield null;
                yield computeUnary(op, inputConstantValue);
            }
            case BinaryExpression(var first, var second, var op) -> {
                var firstConstantValue = analyseExpression(first);
                var secondConstantValue = analyseExpression(second);
                if (firstConstantValue == null || secondConstantValue == null) yield null;
                yield computeBinary(op, firstConstantValue, secondConstantValue);
            }
            case ShortedBinaryExpression(var first, var second, var op) -> {
                var firstConstantValue = analyseExpression(first);
                var secondConstantValue = analyseExpression(second);
                // If we short after the first ConstantValue, the second expr never runs, and can thus be non-constant
                if (firstConstantValue == null) yield null;
                yield switch (op) {
                    case AND -> firstConstantValue.asBoolean() ? secondConstantValue : firstConstantValue;
                    case OR -> firstConstantValue.asBoolean() ? firstConstantValue : secondConstantValue;
                };
            }
            case TernaryExpression(var condition, var ifTrue, var ifFalse) -> {
                var conditionConstantValue = analyseExpression(condition);
                var trueConstantValue = analyseExpression(ifTrue);
                var falseConstantValue = analyseExpression(ifFalse);
                // If the condition is constant, we don't care about that branch that's never taken
                if (conditionConstantValue == null) yield null;
                yield conditionConstantValue.asBoolean() ? trueConstantValue : falseConstantValue;
            }
            default -> {
                expr.getChildren().forEach(this::analyse);
                yield null;
            }
        };
        if (value != null) metadata.put(expr, CONSTANT_VALUE, value);
        return value;
    }

    private @Nullable ConstantValue computeBinary(BinaryExpression.Operator op, ConstantValue first, ConstantValue second) {
        record Pair(ConstantValue first, ConstantValue second) {}
        var pair = new Pair(first, second);

        return switch (op) {
            case PLUS -> switch (pair) {
                case Pair(ConstantValue.Number(var a), ConstantValue.Number(var b)) -> new ConstantValue.Number(a + b);
                case Pair(ConstantValue.String(var a), ConstantValue.String(var b)) -> new ConstantValue.String(a + b);
                default -> null;
            };
            case MINUS -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? new ConstantValue.Number(a - b) : null;
            case MULTIPLY -> switch (pair) {
                case Pair(ConstantValue.Number(var a), ConstantValue.Number(var b)) -> new ConstantValue.Number(a * b);
                case Pair(ConstantValue.String(var a), ConstantValue.Number(var b)) -> new ConstantValue.String(a.repeat((int) b));
                default -> null;
            };
            case DIVIDE -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? new ConstantValue.Number(a / b) : null;
            case MODULO -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? new ConstantValue.Number(a % b) : null;
            case EXPONENT -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? new ConstantValue.Number(Math.pow(a, b)) : null;
            case AND -> switch (pair) {
                case Pair(ConstantValue.Number(var a), ConstantValue.Number(var b)) -> new ConstantValue.Number((int) a & (int) b);
                case Pair(ConstantValue.Boolean a, ConstantValue.Boolean b) -> ConstantValue.Boolean.of(a.value() && b.value());
                default -> null;
            };
            case OR -> switch (pair) {
                case Pair(ConstantValue.Number(var a), ConstantValue.Number(var b)) -> new ConstantValue.Number((int) a | (int) b);
                case Pair(ConstantValue.Boolean a, ConstantValue.Boolean b) -> ConstantValue.Boolean.of(a.value() || b.value());
                default -> null;
            };
            case XOR -> switch (pair) {
                case Pair(ConstantValue.Number(var a), ConstantValue.Number(var b)) -> new ConstantValue.Number((int) a ^ (int) b);
                case Pair(ConstantValue.Boolean a, ConstantValue.Boolean b) -> ConstantValue.Boolean.of(a.value() ^ b.value());
                default -> null;
            };
            case EQUALS -> ConstantValue.Boolean.of(first.equals(second));
            case NOT_EQUALS -> ConstantValue.Boolean.of(!first.equals(second));
            case LESS_THAN -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? ConstantValue.Boolean.of(a < b) : null;
            case GREATER_THAN -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? ConstantValue.Boolean.of(a > b) : null;
            case LESS_THAN_EQUAL -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? ConstantValue.Boolean.of(a <= b) : null;
            case GREATER_THAN_EQUAL -> pair instanceof Pair(ConstantValue.Number(var a), ConstantValue.Number(var b))
                    ? ConstantValue.Boolean.of(a >= b) : null;
            case IN, ASSIGN -> null;
        };
    }

    private static @Nullable ConstantValue computeUnary(UnaryExpression.Operator op, ConstantValue value) {
        return switch (op) {
            case NOT -> value instanceof ConstantValue.Boolean booleanValue
                    ? ConstantValue.Boolean.of(!booleanValue.value()) : null;
            case MINUS -> value instanceof ConstantValue.Number(var Number)
                    ? new ConstantValue.Number(-Number) : null;
            case BITWISE_NOT -> value instanceof ConstantValue.Number(var Number)
                    ? new ConstantValue.Number(~(int) Number) : null;
            default -> null;
        };
    }
}

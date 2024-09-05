package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public class ExpressionInterpreter {
    public static Value evaluate(Expression expression, EvaluationContext context) {
        return switch (expression) {
            case ArrayInitializerExpression e -> evaluateArrayInit(context, e);
            case ObjectInitializerExpression e -> evaluateObjectInit(context, e);
            
            case AssignmentExpression e -> evaluateAssignment(context, e);
            case BinaryExpression e -> BinaryOperatorInterpreter.evaluate(e.op(), evaluate(e.first(), context), evaluate(e.second(), context), context.getPos(e).orElse(null), context);
            case ShortedBinaryExpression e -> evaluateShortedBinary(context, e);
            case UnaryExpression e -> UnaryExpressionInterpreter.evaluate(e.op(), evaluate(e.input(), context), context.getPos(e).orElse(null), context);
            case UnaryModificationExpression e -> evaluateUnaryModification(context, e);
            case ValueExpression e -> e.value();
            
            case FunctionCallExpression e -> evaluateFunctionCall(context, e);
            case FunctionExpression e -> evaluateFunctionCreation(context, e);
            case TernaryExpression e -> evaluate(e.condition(), context).asBoolean() ? evaluate(e.ifTrue(), context) : evaluate(e.ifFalse(), context);
            
            case IndexExpression e -> evaluateIndex(context, e);
            case IsInstanceExpression e -> evaluateIsInstance(context, e);
            case PropertyAccessExpression e -> evaluatePropertyAccess(context, e);
            case RootExpression ignored -> context.root();
            case VariableAccessExpression e -> context.variables().getVariable(e.name(), context.getPos(e).orElse(null));
            
            case ErrorExpression e -> throw new IllegalStateException("Tried to use error expression", e.error());
            default -> throw new UnsupportedOperationException("Unsupported expression: %s".formatted(expression));
        };
    }

    private static void assign(Reference target, Value value, EvaluationContext context) {
        switch (target) {
            case VariableAccessExpression expression -> context.variables().getVariable(expression.name(), context.getPos(expression).orElse(null));
            case IndexExpression expression -> {
                var parent = evaluate(expression.parent(), context);
                var index = evaluate(expression.index(), context);
                parent.set(index, value, context.createFunctionContext(context.getPos(expression).orElse(null)));
            }
            case PropertyAccessExpression expression -> {
                var parent = evaluate(expression.parent(), context);
                if (parent instanceof Value.ObjectValue objectValue) {
                    objectValue.value().put(expression.name(), value);
                } else {
                    String message = "Tried to write property %s of %s. Only objects have writable properties.".formatted(expression.name(), parent);
                    throw new EvaluationException(context.config(), message, context.getPos(expression).orElse(null));
                }
            }
            case ErrorExpression e -> throw new IllegalStateException("Tried to use error expression", e.error());
            default -> throw new UnsupportedOperationException("Unsupported target: %s".formatted(target));
        }
    }

    private static Value.ArrayValue evaluateArrayInit(EvaluationContext context, ArrayInitializerExpression expression) {
        return new Value.ArrayValue(expression.contents().stream()
                .map(subExpression -> evaluate(subExpression, context))
                .toList());
    }

    private static Value evaluateObjectInit(EvaluationContext context, ObjectInitializerExpression expression) {
        var object = new Value.ObjectValue();
        expression.contents().forEach(entry -> object.value().put(entry.name(), evaluate(entry.value(), context)));
        return object;
    }

    private static Value evaluateAssignment(EvaluationContext context, AssignmentExpression expression) {
        var original = expression.operator() == BinaryExpression.Operator.ASSIGN ? null : evaluate(expression.target(), context);
        var value = evaluate(expression.value(), context);
        assign(expression.target(), BinaryOperatorInterpreter.evaluate(expression.operator(), original, value, context.getPos(expression).orElse(null), context), context);
        return value;
    }

    private static Value evaluateShortedBinary(EvaluationContext context, ShortedBinaryExpression e) {
        var op = e.op();
        return switch (op) {
            case OR -> {
                var firstVal = evaluate(e.first(), context);
                if (firstVal.asBoolean()) yield firstVal;
                yield evaluate(e.second(), context);
            }
            case AND -> {
                var firstVal = evaluate(e.first(), context);
                if (!firstVal.asBoolean()) yield firstVal;
                yield evaluate(e.second(), context);
            }
        };
    }

    private static Value evaluateUnaryModification(EvaluationContext context, UnaryModificationExpression expression) {
        var oldValue = evaluate(expression.target(), context);
        var newValue = UnaryExpressionInterpreter.evaluate(expression.operator(), oldValue, context.getPos(expression).orElse(null), context);
        assign(expression.target(), newValue, context);

        return expression.postfix() ? oldValue : newValue;
    }

    private static Value evaluateFunctionCall(EvaluationContext context, FunctionCallExpression expression) {
        var value = evaluate(expression.function(), context);
        if (!(value instanceof Value.FunctionValue functionValue)) {
            throw new EvaluationException(context.config(), "Tried to call %s, not a function".formatted(value), context.getPos(expression).orElse(null));
        }
        var args = expression.arguments().stream().map(argument -> evaluate(argument, context)).toList();
        
        return switch (functionValue.function()) {
            case LegacyRuntimePatchFunction function -> function.execute(context, args, context.getPos(expression).orElse(null));
            case PatchFunction.RuntimePatchFunction other -> throw new IllegalStateException("Unsupported function from foreign runtime: " + other);
            case PatchFunction.BuiltInPatchFunction function -> function.execute(context.createFunctionContext(context.getPos(expression).orElse(null)), args);
        };
    }

    private static Value evaluateFunctionCreation(EvaluationContext context, FunctionExpression expression) {
        return new Value.FunctionValue(new LegacyRuntimePatchFunction(expression.body(), expression.args(), context));
    }

    private static Value evaluateIndex(EvaluationContext context, IndexExpression expression) {
        var parent = evaluate(expression.parent(), context);
        var index = evaluate(expression.index(), context);
        
        return parent.get(index, context.createFunctionContext(context.getPos(expression).orElse(null)));
    }

    private static Value.BooleanValue evaluateIsInstance(EvaluationContext context, IsInstanceExpression expression) {
        var value = evaluate(expression.input(), context);
        return Value.BooleanValue.of(switch (expression.type()) {
            case NUMBER -> value instanceof Value.NumberValue;
            case STRING -> value instanceof Value.StringValue;
            case BOOLEAN -> value instanceof Value.BooleanValue;
            case ARRAY -> value instanceof Value.ArrayValue;
            case OBJECT -> value instanceof Value.ObjectValue;
            case NULL -> value instanceof Value.NullValue;
            case FUNCTION -> value instanceof Value.FunctionValue;
        });
    }

    private static Value evaluatePropertyAccess(EvaluationContext context, PropertyAccessExpression expression) {
        var parent = evaluate(expression.parent(), context);
        return parent.getProperty(expression.name(), context.createFunctionContext(context.getPos(expression).orElse(null)));
    }
}

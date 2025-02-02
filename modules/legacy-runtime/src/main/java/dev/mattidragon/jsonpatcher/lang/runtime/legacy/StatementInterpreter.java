package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import static dev.mattidragon.jsonpatcher.lang.runtime.legacy.ExpressionInterpreter.evaluate;

public class StatementInterpreter {
    public static void execute(Statement statement, EvaluationContext context) {
        switch (statement) {
            case ApplyStatement s -> executeApply(context, s);
            case BlockStatement s -> executeBlock(context, s);
            case BreakStatement s -> throw new BreakStatement.BreakException();
            case ContinueStatement s -> throw new ContinueStatement.ContinueException();
            case DeleteStatement s -> delete(s.target(), context);
            case EmptyStatement s -> {}
            case ExpressionStatement s -> evaluate(s.expression(), context);
            case ForEachLoopStatement s -> executeForEachLoop(context, s);
            case ForLoopStatement s -> executeForLoop(context, s);
            case WhileLoopStatement s -> executeWhileLoop(context, s);
            case FunctionDeclarationStatement s -> context.variables().createVariable(s.name(), evaluate(s.value(), context), false, context.getPos(s.value()).orElse(null));
            case IfStatement s -> executeIf(context, s);
            case ImportStatement s -> context.variables().createVariable(s.variableName(), context.findLibrary(s.libraryName(), context.createFunctionContext(context.getPos(s).orElse(null))), false, context.getPos(s).orElse(null));
            case ReturnStatement s -> throw new ReturnException(s.value().map(expression -> evaluate(expression, context)).orElse(Value.NullValue.NULL), context.getPos(s).orElse(null));
            case VariableCreationStatement s -> context.variables().createVariable(s.name(), evaluate(s.initializer(), context), s.mutable(), context.getPos(s).orElse(null));

            case ErrorStatement s -> throw new IllegalStateException("Tried to execute error statement:\n" + s.diagnostic().toDisplay());
            default -> throw new UnsupportedOperationException("Unsupported statement: %s".formatted(statement));
        }
    }

    private static void delete(Reference target, EvaluationContext context) {
        switch (target) {
            case IndexExpression e -> {
                var parent = evaluate(e.parent(), context);
                var index = evaluate(e.index(), context);
                parent.delete(index, context.createFunctionContext(context.getPos(e).orElse(null)));
            }
            case VariableAccessExpression e -> context.variables().deleteVariable(e.name(), context.getPos(e).orElse(null));
            case PropertyAccessExpression e -> {
                var parent = evaluate(e.parent(), context);
                if (parent instanceof Value.ObjectValue(var map, var frozen)) {
                    if (frozen) {
                        var message = "Tried to delete to property %s of %s, but it is frozen.".formatted(e.name(), parent);
                        throw new EvaluationException(context.config(), message, context.getPos(e).orElse(null));
                    }
                    map.remove(e.name());
                } else {
                    String message = "Tried to delete property %s of %s. Only objects have writable properties.".formatted(e.name(), parent);
                    throw new EvaluationException(context.config(), message, context.getPos(e).orElse(null));
                }
            }
            case ErrorExpression e -> throw new IllegalStateException("Tried to use error expression:\n" + e.diagnostic().toDisplay());
            default -> throw new UnsupportedOperationException("Unsupported target: %s".formatted(target));
        }
    }

    private static void executeApply(EvaluationContext context, ApplyStatement statement) {
        var root = evaluate(statement.root(), context);
        if (!(root instanceof Value.ObjectValue objectValue)) {
            throw new EvaluationException(context.config(), "Only objects can be used in apply statements, tried to use %s".formatted(root), context.getPos(statement).orElse(null));
        }
        execute(statement.action(), context.withRoot(objectValue));
    }

    private static void executeBlock(EvaluationContext context, BlockStatement statement) {
        context = context.newScope();
        for (var child : statement.statements()) {
            execute(child, context);
        }
    }

    private static void executeForEachLoop(EvaluationContext context, ForEachLoopStatement statement) {
        var values = evaluate(statement.iterable(), context);
        if (!(values instanceof Value.ArrayValue(var array, var frozen))) {
            throw new EvaluationException(context.config(), "Can only iterate arrays, tried to iterate %s".formatted(values), context.getPos(statement.iterable()).orElse(null));
        }
        if (frozen) {
            var message = "Tried to iterate %s, but it is frozen.".formatted(values);
            throw new EvaluationException(context.config(), message, context.getPos(statement).orElse(null));
        }
        for (var value : array) {
            var loopContext = context.newScope();
            loopContext.variables().createVariable(statement.variableName(), value, false, context.getPos(statement).orElse(null));
            try {
                execute(statement.body(), loopContext);
            } catch (BreakStatement.BreakException e) {
                break;
            } catch (ContinueStatement.ContinueException e) {
                // Continue
            }
        }
    }

    private static void executeForLoop(EvaluationContext context, ForLoopStatement statement) {
        context = context.newScope();
        for (execute(statement.initializer(), context);
             evaluate(statement.condition(), context).asBoolean();
             execute(statement.incrementer(), context)) {
            try {
                execute(statement.body(), context);
            } catch (BreakStatement.BreakException e) {
                break;
            } catch (ContinueStatement.ContinueException e) {
                // Continue
            }
        }
    }

    private static void executeWhileLoop(EvaluationContext context, WhileLoopStatement statement) {
        while (evaluate(statement.condition(), context).asBoolean()) {
            try {
                execute(statement.body(), context);
            } catch (BreakStatement.BreakException e) {
                break;
            } catch (ContinueStatement.ContinueException e) {
                // Continue
            }
        }
    }

    private static void executeIf(EvaluationContext context, IfStatement statement) {
        if (evaluate(statement.condition(), context).asBoolean()) {
            execute(statement.action(), context);
        } else if (statement.elseAction() != null) {
            execute(statement.elseAction(), context);
        }
    }
}

package io.github.mattidragon.jsonpatcher.lang.runtime.legacy;

import io.github.mattidragon.jsonpatcher.lang.ast.expression.*;
import io.github.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import io.github.mattidragon.jsonpatcher.lang.ast.statement.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import static io.github.mattidragon.jsonpatcher.lang.runtime.legacy.ExpressionInterpreter.evaluate;

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
            case ImportStatement s -> context.variables().createVariable(s.variableName(), context.findLibrary(s.libraryName(), context.getPos(s).orElse(null)), false, context.getPos(s).orElse(null));
            case ReturnStatement s -> throw new ReturnException(s.value().map(expression -> evaluate(expression, context)).orElse(Value.NullValue.NULL), context.getPos(s).orElse(null));
            case VariableCreationStatement s -> context.variables().createVariable(s.name(), evaluate(s.initializer(), context), s.mutable(), context.getPos(s).orElse(null));

            case ErrorStatement s -> throw new IllegalStateException("Tried to execute error statement", s.error());
            default -> throw new UnsupportedOperationException("Unsupported statement: %s".formatted(statement));
        }
    }

    private static void delete(Reference target, EvaluationContext context) {
        switch (target) {
            case IndexExpression e -> {
                var parent = evaluate(e.parent(), context);
                var index = evaluate(e.index(), context);
                if (parent instanceof Value.ObjectValue objectValue) {
                    if (!(index instanceof Value.StringValue stringValue)) {
                        String message = "Tried to index object by %s. Objects can only be indexed by string".formatted(index);
                        throw new EvaluationException(((PatchFunction.BuiltInPatchFunction.Context) context).config(), message, context.getPos(e).orElse(null));
                    }
                    objectValue.remove(stringValue.value(), context.config(), context.getPos(e).orElse(null));
                } else if (parent instanceof Value.ArrayValue arrayValue) {
                    if (!(index instanceof Value.NumberValue numberValue)) {
                        String message = "Tried to index array by %s. Arrays can only be indexed by number.".formatted(index);
                        throw new EvaluationException(((PatchFunction.BuiltInPatchFunction.Context) context).config(), message, context.getPos(e).orElse(null));
                    }
                    arrayValue.remove((int) numberValue.value(), context.config(), context.getPos(e).orElse(null));
                } else {
                    String message = "Tried to index %s with %s. Only arrays and objects are indexable.".formatted(parent, index);
                    throw new EvaluationException(((PatchFunction.BuiltInPatchFunction.Context) context).config(), message, context.getPos(e).orElse(null));
                }
            }
            case VariableAccessExpression e -> context.variables().deleteVariable(e.name(), context.getPos(e).orElse(null));
            case PropertyAccessExpression e -> {
                var parent = evaluate(e.parent(), context);
                if (parent instanceof Value.ObjectValue objectValue) {
                    objectValue.remove(e.name(), context.config(), context.getPos(e).orElse(null));
                } else {
                    String message = "Tried to delete property %s of %s. Only objects have writable properties.".formatted(e.name(), parent);
                    throw new EvaluationException(((PatchFunction.BuiltInPatchFunction.Context) context).config(), message, context.getPos(e).orElse(null));
                }
            }
            case ErrorExpression e -> throw new IllegalStateException("Tried to use error expression", e.error());
            default -> throw new UnsupportedOperationException("Unsupported target: %s".formatted(target));
        }
    }

    private static void executeApply(EvaluationContext context, ApplyStatement statement) {
        var root = evaluate(statement.root(), context);
        if (!(root instanceof Value.ObjectValue objectValue)) {
            throw new EvaluationException(((PatchFunction.BuiltInPatchFunction.Context) context).config(), "Only objects can be used in apply statements, tried to use %s".formatted(root), context.getPos(statement).orElse(null));
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
        if (!(values instanceof Value.ArrayValue arrayValue)) {
            throw new EvaluationException(context.config(), "Can only iterate arrays, tried to iterate %s".formatted(values), context.getPos(statement.iterable()).orElse(null));
        }
        for (var value : arrayValue.value()) {
            var loopContext = context.newScope();
            loopContext.variables().createVariable(statement.variableName(), value, false, context.getPos(statement).orElse(null));
            try {
                execute(statement, loopContext);
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

package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record LegacyRuntimePatchFunction(Statement body, FunctionArguments args, EvaluationContext context) implements PatchFunction.RuntimePatchFunction {
    public Value execute(EvaluationContext context, List<Value> args, SourceSpan callPos) {
        if (args.size() < this.args.requiredArguments()) {
            throw new EvaluationException(context.config(), "Incorrect function argument count: expected at least %s but found %s".formatted(this.args.requiredArguments(), args.size()), callPos);
        }
        var argEntryCount = this.args.arguments().size();
        if (!this.args.varargs() && args.size() > argEntryCount) {
            throw new EvaluationException(context.config(), "Incorrect function argument count: expected at most %s but found %s".formatted(argEntryCount, args.size()), callPos);
        }

        // We use the context the function was created in, not the one it was called in.
        // This allows for closures if we ever allow a function to escape its original scope
        var functionContext = this.context.newScope();

        for (int i = 0; i < argEntryCount; i++) {
            var argument = this.args.arguments().get(i);

            Value value;
            if (i >= args.size()) {
                // Default arguments past the passed in values
                value = ExpressionInterpreter.evaluate(argument.defaultValue()
                                .orElseThrow(() -> new IllegalStateException("No value for non-default argument got past checks")),
                        functionContext);
            } else if (i == argEntryCount - 1 && this.args.varargs()) {
                // If we're on the last argument of a varargs function, grab 'em all
                value = new Value.ArrayValue(args.stream().skip(i).toList());
            } else {
                // Normal argument passing
                value = args.get(i);
            }

            switch (argument.target()) {
                case FunctionArgument.Target.Variable variable -> functionContext.variables().createVariableUnsafe(variable.name(), value, false);
                case FunctionArgument.Target.Root ignored when value instanceof Value.ObjectValue root
                        -> functionContext = functionContext.withRoot(root);
                case FunctionArgument.Target.Root.INSTANCE ->
                        throw new EvaluationException(context.config(), "Only objects can be used in root arguments, tried to use %s".formatted(value), callPos);
            }
        }

        try {
            StatementInterpreter.execute(body, functionContext);
        } catch (ReturnException r) {
            return r.value;
        } catch (EvaluationException e) {
            throw new EvaluationException(context.config(), "Error while executing function", callPos, e);
        }

        return Value.NullValue.NULL;
    }
}
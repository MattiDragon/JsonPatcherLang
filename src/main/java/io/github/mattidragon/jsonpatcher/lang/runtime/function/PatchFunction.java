package io.github.mattidragon.jsonpatcher.lang.runtime.function;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;
import io.github.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.List;

public sealed interface PatchFunction {
    Value execute(EvaluationContext context, List<Value> args, SourceSpan callPos);

    default PatchFunction bind(Value value) {
        return Libraries.FunctionsLibrary.bind(new Value.FunctionValue(this), value).function();
    }

    @FunctionalInterface
    non-sealed interface BuiltInPatchFunction extends PatchFunction {
        default BuiltInPatchFunction argCount(int count) {
            return (context, args, callPos) -> {
                if (args.size() != count) {
                    throw new EvaluationException(context.config(), "Incorrect function argument count: expected %s but found %s".formatted(count, args.size()), callPos);
                }
                return execute(context, args, callPos);
            };
        }
    }

    record DefinedPatchFunction(Statement body, FunctionArguments args, EvaluationContext context) implements PatchFunction {
        @Override
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
                    value = argument.defaultValue()
                            .orElseThrow(() -> new IllegalStateException("No value for non-default argument got past checks"))
                            .evaluate(functionContext);
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
                body.run(functionContext);
            } catch (ReturnException r) {
                return r.value;
            } catch (EvaluationException e) {
                throw new EvaluationException(context.config(), "Error while executing function", callPos, e);
            }

            return Value.NullValue.NULL;
        }
    }
}

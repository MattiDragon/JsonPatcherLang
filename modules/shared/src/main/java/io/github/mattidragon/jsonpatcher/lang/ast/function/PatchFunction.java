package io.github.mattidragon.jsonpatcher.lang.ast.function;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.List;

public sealed interface PatchFunction {
    default PatchFunction bind(Value value) {
        return Libraries.FunctionsLibrary.bind(new Value.FunctionValue(this), value).function();
    }

    @FunctionalInterface
    non-sealed interface BuiltInPatchFunction extends PatchFunction {
        Value execute(Context context, List<Value> args, SourceSpan callPos);
        
        default BuiltInPatchFunction argCount(int count) {
            return (context, args, callPos) -> {
                if (args.size() != count) {
                    throw new EvaluationException(context.config(), "Incorrect function argument count: expected %s but found %s".formatted(count, args.size()), callPos);
                }
                return execute(context, args, callPos);
            };
        }
        
        interface Context {
            LangConfig config();
            Value execute(PatchFunction function, List<Value> args, SourceSpan callPos);
            void log(Value value);
        }
    }

    /**
     * A function defined created by a runtime. Only runtimes are allowed to create these,
     * and they must not be passed across different runtimes.
     */
    non-sealed interface RuntimePatchFunction extends PatchFunction {}
}

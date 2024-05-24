package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.FunctionArguments;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.PatchFunction;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;

import java.util.List;
import java.util.Optional;

public record FunctionExpression(Statement body, FunctionArguments args, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return new Value.FunctionValue(new PatchFunction.DefinedPatchFunction(body, args, context));
    }
}

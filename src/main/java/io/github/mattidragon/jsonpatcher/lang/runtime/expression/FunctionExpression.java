package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArgument;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArguments;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.PatchFunction;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;

import java.util.ArrayList;
import java.util.Optional;

public record FunctionExpression(Statement body, FunctionArguments args, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return new Value.FunctionValue(new PatchFunction.DefinedPatchFunction(body, args, context));
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        var list = new ArrayList<ProgramNode>();
        list.add(body);
        args.arguments().stream()
                .map(FunctionArgument::defaultValue)
                .flatMap(Optional::stream)
                .forEach(list::add);
        return list;
    }
}

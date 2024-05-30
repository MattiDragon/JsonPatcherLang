package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record ShortedBinaryExpression(Expression first, Expression second, Operator op, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return op.apply(first, second, context);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(first, second);
    }

    public interface Operator {
        Value apply(Expression first, Expression second, EvaluationContext context);
        Operator AND = (first, second, context) -> {
            var firstVal = first.evaluate(context);
            if (!firstVal.asBoolean()) return firstVal;
            return second.evaluate(context);
        };
        Operator OR = (first, second, context) -> {
            var firstVal = first.evaluate(context);
            if (firstVal.asBoolean()) return firstVal;
            return second.evaluate(context);
        };
    }
}

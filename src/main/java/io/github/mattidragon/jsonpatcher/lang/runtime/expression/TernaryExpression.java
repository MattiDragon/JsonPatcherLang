package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

public record TernaryExpression(Expression condition, Expression ifTrue, Expression ifFalse, SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return condition.evaluate(context).asBoolean() ? ifTrue.evaluate(context) : ifFalse.evaluate(context);
    }
}

package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;

public record ExpressionStatement(Expression expression) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        expression.evaluate(context);
    }

    @Override
    public SourceSpan getPos() {
        return expression.pos();
    }
}

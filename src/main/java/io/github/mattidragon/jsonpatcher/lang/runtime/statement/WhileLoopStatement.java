package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;

import java.util.List;

public record WhileLoopStatement(Expression condition, Statement body, SourceSpan pos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        while (condition.evaluate(context).asBoolean()) {
            try {
                body.run(context);
            } catch (BreakStatement.BreakException e) {
                break;
            } catch (ContinueStatement.ContinueException e) {
                // Continue
            }
        }
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(condition, body);
    }
}

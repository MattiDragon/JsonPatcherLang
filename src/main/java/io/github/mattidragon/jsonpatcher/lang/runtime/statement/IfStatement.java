package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public record IfStatement(Expression condition, Statement action, @Nullable Statement elseAction, SourceSpan pos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        if (condition.evaluate(context).asBoolean()) {
            action.run(context);
        } else if (elseAction != null) {
            elseAction.run(context);
        }
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        var list = new ArrayList<ProgramNode>();
        list.add(condition);
        list.add(action);
        if (elseAction != null) {
            list.add(elseAction);
        }
        return list;
    }
}

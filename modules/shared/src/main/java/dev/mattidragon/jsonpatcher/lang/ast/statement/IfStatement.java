package dev.mattidragon.jsonpatcher.lang.ast.statement;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

public record IfStatement(Expression condition, Statement action, @Nullable Statement elseAction) implements Statement {
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

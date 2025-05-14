package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.ArrayList;
import java.util.List;

public record FunctionCallExpression(Expression function, List<Expression> arguments) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        var list = new ArrayList<>(arguments);
        list.addFirst(function);
        return list;
    }
}

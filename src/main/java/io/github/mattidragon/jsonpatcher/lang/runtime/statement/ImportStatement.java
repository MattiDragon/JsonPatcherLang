package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;

import java.util.List;

public record ImportStatement(String libraryName, String variableName, SourceSpan pos, SourceSpan variablePos, SourceSpan namePos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        context.variables().createVariable(variableName, context.findLibrary(libraryName, pos), false, pos);
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}

package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;

public interface Statement extends ProgramNode {
    void run(EvaluationContext context);
    SourceSpan getPos();

    default EvaluationException error(EvaluationContext context, String message) {
        return new EvaluationException(context.config(), message, getPos());
    }
}

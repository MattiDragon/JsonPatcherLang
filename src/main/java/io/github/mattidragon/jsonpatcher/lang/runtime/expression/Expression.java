package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

public interface Expression extends ProgramNode {
    Value evaluate(EvaluationContext context);
    SourceSpan pos();

    default EvaluationException error(EvaluationContext context, String message) {
        return new EvaluationException(context.config(), message, pos());
    }
}

package io.github.mattidragon.jsonpatcher.lang.runtime;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.jetbrains.annotations.Nullable;

public class EvaluationException extends PositionedException {
    @Nullable
    private final SourceSpan pos;

    public EvaluationException(LangConfig config, String message, @Nullable SourceSpan pos) {
        super(config, message);
        this.pos = pos;
    }

    public EvaluationException(LangConfig config, String message, @Nullable SourceSpan pos, EvaluationException cause) {
        super(config, message, cause);
        this.pos = pos;
    }

    @Override
    protected String getBaseMessage() {
        return "Error while applying patch";
    }

    @Override
    @Nullable
    public SourceSpan getPos() {
        return pos;
    }
}

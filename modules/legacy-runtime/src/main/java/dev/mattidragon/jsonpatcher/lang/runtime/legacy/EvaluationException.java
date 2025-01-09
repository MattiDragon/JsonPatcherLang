package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.error.PositionedException;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

public class EvaluationException extends PositionedException {
    @Nullable
    private final SourceSpan pos;

    public EvaluationException(LangConfig config, String message, @Nullable SourceSpan pos) {
        super(config, message);
        this.pos = pos;
    }

    public EvaluationException(LangConfig config, String message, @Nullable SourceSpan pos, RuntimeException cause) {
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

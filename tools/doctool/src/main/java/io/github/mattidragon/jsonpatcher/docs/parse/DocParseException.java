package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.ast.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jetbrains.annotations.Nullable;

public class DocParseException extends PositionedException {
    @Nullable
    private final SourceSpan pos;

    protected DocParseException(LangConfig config, String message, @Nullable SourceSpan pos) {
        super(config, message);
        this.pos = pos;
    }

    public DocParseException(LangConfig config, String message, SourcePos pos) {
        this(config, message, new SourceSpan(pos, pos));
    }

    @Override
    protected String getBaseMessage() {
        return "Error while parsing docs";
    }

    @Override
    public @Nullable SourceSpan getPos() {
        return pos;
    }
}

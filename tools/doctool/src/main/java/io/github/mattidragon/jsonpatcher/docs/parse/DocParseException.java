package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.jetbrains.annotations.Nullable;

public class DocParseException extends PositionedException {
    @Nullable
    private final SourceSpan pos;

    protected DocParseException(String message, @Nullable SourceSpan pos) {
        super(message);
        this.pos = pos;
    }

    public DocParseException(String message, SourcePos pos) {
        this(message, new SourceSpan(pos, pos));
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

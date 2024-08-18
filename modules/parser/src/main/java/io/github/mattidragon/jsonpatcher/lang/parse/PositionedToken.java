package io.github.mattidragon.jsonpatcher.lang.parse;

import io.github.mattidragon.jsonpatcher.lang.ast.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;

public record PositionedToken(SourceSpan pos, Token token) {
    public SourcePos from() {
        return pos.from();
    }

    public SourcePos to() {
        return pos.to();
    }
}

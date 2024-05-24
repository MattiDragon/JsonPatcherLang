package io.github.mattidragon.jsonpatcher.lang.parse;

public record PositionedToken(SourceSpan pos, Token token) {
    public SourcePos getFrom() {
        return pos.from();
    }

    public SourcePos getTo() {
        return pos.to();
    }

    public Token getToken() {
        return token;
    }

    public SourceSpan getPos() {
        return pos;
    }
}

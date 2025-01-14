package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

public record IllegalSourceSpanError(SourceSpan span) implements PosCheckError {
    @Override
    public String message() {
        return "Illegal source span: " + span;
    }
}

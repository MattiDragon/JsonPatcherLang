package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

record IllegalSourceSpanError(SourceSpan span) implements PosCheckError {
    @Override
    public SourceSpan pos() {
        // Try to fix span by picking one file and correctly ordering the positions within it
        var file = span.from().file();
        var fromRow = span.from().row();
        var toRow = span.to().row();

        var fixedFromCol = toRow == fromRow ? Math.min(span.from().column(), span.to().column())
                : fromRow < toRow ? span.from().column()
                : span.to().column();
        var fixedToCol = toRow == fromRow ? Math.max(span.from().column(), span.to().column())
                : fromRow > toRow ? span.from().column()
                : span.to().column();

        return new SourceSpan(
                new SourcePos(
                        file,
                        Math.min(fromRow, toRow),
                        fixedFromCol),
                new SourcePos(
                        file,
                        Math.max(fromRow, toRow),
                        fixedToCol));
    }

    @Override
    public @Nullable ProgramNode node() {
        return null;
    }

    @Override
    public String message() {
        return "Illegal source span: " + span;
    }

    @Override
    public String id() {
        return PosCheckDiagnostics.ILLEGAL_SOURCE_SPAN;
    }
}

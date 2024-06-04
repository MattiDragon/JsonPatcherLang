package io.github.mattidragon.jsonpatcher.lang.parse;

public record SourceSpan(SourcePos from, SourcePos to) {
    public boolean contains(SourcePos pos) {
        return to.row() >= pos.row() 
               && (to.row() != pos.row() || to.column() >= pos.column()) 
               && from.row() <= pos.row() 
               && (from.row() != pos.row() || from.column() <= pos.column());
    }
}

package io.github.mattidragon.jsonpatcher.lang.ast;

public record SourceSpan(SourcePos from, SourcePos to) {
    public static SourceSpan between(SourceSpan first, SourceSpan second) {
        return new SourceSpan(first.from(), second.to());
    }
    
    public boolean contains(SourcePos pos) {
        return to.row() >= pos.row() 
               && (to.row() != pos.row() || to.column() >= pos.column()) 
               && from.row() <= pos.row() 
               && (from.row() != pos.row() || from.column() <= pos.column());
    }
}

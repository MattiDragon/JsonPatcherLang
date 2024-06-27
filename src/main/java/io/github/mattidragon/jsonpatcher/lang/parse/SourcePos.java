package io.github.mattidragon.jsonpatcher.lang.parse;

public record SourcePos(SourceFile file, int row, int column) {
    public SourcePos offset(int offset) {
        return new SourcePos(file, row, column + offset);
    }
    
    public SourceSpan toSpan() {
        return new SourceSpan(this, this);
    }
}

package dev.mattidragon.jsonpatcher.lang.ast;

import java.util.Comparator;

public record SourcePos(SourceFile file, int row, int column) implements Comparable<SourcePos> {
    public SourcePos offset(int offset) {
        return new SourcePos(file, row, column + offset);
    }
    
    public SourceSpan toSpan() {
        return new SourceSpan(this, this);
    }

    @Override
    public int compareTo(SourcePos other) {
        if (other.file != this.file) return 0;
        return Comparator.comparing(SourcePos::row).thenComparing(SourcePos::column).compare(this, other);
    }
}

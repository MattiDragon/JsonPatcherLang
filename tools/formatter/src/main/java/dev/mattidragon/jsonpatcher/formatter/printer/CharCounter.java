package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

public class CharCounter extends PrintTarget {
    private int indent;
    private int count;
    private boolean multiline;

    public CharCounter(PrettyPrintOptions options, TreeMetadata treeMetadata, int indent, int count) {
        super(options, treeMetadata);
        this.indent = indent;
        this.count = count;
    }

    @Override
    public PrintTarget pushIndent() {
        indent += options.indentColumns;
        return this;
    }

    @Override
    public PrintTarget popIndent() {
        indent -= options.indentColumns;
        return this;
    }

    @Override
    public PrintTarget newLine() {
        multiline = true;
        count = 0;
        return this;
    }

    @Override
    public boolean isClosed() {
        return isLong();
    }

    @Override
    public CharCounter newCharCounter() {
        return new CharCounter(options, treeMetadata, indent, count);
    }

    @Override
    protected void writeText(String text) {
        if (text.contains("\n")) throw new IllegalStateException("writeText may not be called with newlines");
        count += text.codePointCount(0, text.length());
    }

    @Override
    protected boolean failOnError() {
        return options.failOnError;
    }

    public boolean isMultiline() {
        return multiline;
    }

    public boolean isLongLine() {
        return count > options.maxColumns;
    }

    public boolean isLong() {
        return isMultiline() || isLongLine();
    }
}

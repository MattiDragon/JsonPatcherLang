package dev.mattidragon.jsonpatcher.formatter.printer;

public class CharCounter extends PrintTarget {
    private final PrettyPrintOptions options;
    private int indent;
    private int count;
    private boolean multiline;

    public CharCounter(PrettyPrintOptions options, int indent, int count) {
        this.options = options;
        this.indent = indent;
        this.count = count;
    }

    @Override
    public PrintTarget pushIndent() {
        indent += 4;
        return this;
    }

    @Override
    public PrintTarget popIndent() {
        indent -= 4;
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
        return isMultiline();
    }

    @Override
    public CharCounter newCharCounter() {
        return new CharCounter(options, indent, count);
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
        return multiline || count > options.maxColumns;
    }
}

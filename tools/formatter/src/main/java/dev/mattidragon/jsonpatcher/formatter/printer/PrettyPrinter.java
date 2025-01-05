package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

public class PrettyPrinter extends PrintTarget {
    private final StringBuilder out = new StringBuilder();

    private int indent = 0;
    private int columnLength;

    public PrettyPrinter(PrettyPrintOptions options, TreeMetadata treeMetadata) {
        super(options, treeMetadata);
    }

    @Override
    public PrettyPrinter pushIndent() {
        indent++;
        return this;
    }

    @Override
    public PrettyPrinter popIndent() {
        indent--;
        return this;
    }

    @Override
    public PrettyPrinter newLine() {
        out.append('\n').append(options.indent.repeat(indent));
        columnLength = 0;
        return this;
    }

    @Override
    public CharCounter newCharCounter() {
        return new CharCounter(options, treeMetadata, indent, columnLength);
    }

    @Override
    public void writeText(String text) {
        if (text.contains("\n")) throw new IllegalStateException("writeText may not be called with newlines");
        out.append(text);
        columnLength += text.codePointCount(0, text.length());
    }

    @Override
    protected boolean failOnError() {
        return options.failOnError;
    }

    public String getOutput() {
        return out.toString();
    }
}

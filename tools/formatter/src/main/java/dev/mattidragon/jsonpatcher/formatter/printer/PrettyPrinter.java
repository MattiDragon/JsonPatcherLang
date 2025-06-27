package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import org.jspecify.annotations.Nullable;

public class PrettyPrinter extends PrintTarget {
    private final StringBuilder out = new StringBuilder();

    private int indent = 0;
    private int columnLength;

    private @Nullable String pendingIndent = null;

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
        out.append('\n');
        pendingIndent = options.indent.repeat(indent);
        columnLength = options.indentColumns * indent;
        return this;
    }

    @Override
    public CharCounter newCharCounter() {
        return new CharCounter(options, treeMetadata, indent, columnLength);
    }

    @Override
    public void writeText(String text) {
        if (text.contains("\n")) throw new IllegalStateException("writeText may not be called with newlines");
        if (pendingIndent != null) {
            out.append(pendingIndent);
            pendingIndent = null;
        }
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

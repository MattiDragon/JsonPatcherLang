package dev.mattidragon.jsonpatcher.formatter.printer;

public class PrettyPrintOptions {
    // TODO: Use indent in pretty-printer
    public final String indent;
    public final int indentColumns;
    public final int maxColumns;
    public final boolean failOnError;

    private PrettyPrintOptions(String indent, int indentColumns, int maxColumns, boolean failOnError) {
        this.indent = indent;
        this.indentColumns = indentColumns;
        this.maxColumns = maxColumns;
        this.failOnError = failOnError;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String indent = "    ";
        private int indentColumns = 4;
        private int maxColumns = 80;
        private boolean failOnError = true;

        public Builder indent(String indent) {
            return indent(indent, indent.replace("\t", "    ").length());
        }

        public Builder indent(String indent, int columns) {
            this.indent = indent;
            indentColumns = columns;
            return this;
        }

        public Builder maxColumns(int maxColumns) {
            this.maxColumns = maxColumns;
            return this;
        }

        public Builder dontFailOnError() {
            this.failOnError = false;
            return this;
        }

        public PrettyPrintOptions build() {
            return new PrettyPrintOptions(indent, indentColumns, maxColumns, failOnError);
        }
    }
}

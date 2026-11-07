package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.NumberStyle;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public abstract class PrintTarget extends StringPrinter {
    private static final Map<NumberStyle, DecimalFormat> NUMBER_FORMATS = Map.of(
            NumberStyle.DECIMAL, new DecimalFormat("0.##########"),
            NumberStyle.INTEGER, new DecimalFormat("0")
    );

    protected final PrettyPrintOptions options;
    protected final TreeMetadata treeMetadata;

    public PrintTarget(PrettyPrintOptions options, TreeMetadata treeMetadata) {
        this.options = options;
        this.treeMetadata = treeMetadata;
    }

    /**
     * Returns {@code true} if this target will ignore any future inputs.
     * Producers can use this to terminate early.
     */
    public boolean isClosed() {
        return false;
    }

    protected abstract boolean failOnError();

    public final <T> Optional<T> getMetadata(MetadataHolder node, MetadataKey<T> key) {
        return treeMetadata.get(node, key);
    }

    public abstract CharCounter newCharCounter();

    public abstract PrintTarget pushIndent();

    public abstract PrintTarget popIndent();

    public abstract PrintTarget newLine();

    protected abstract void writeText(String text);

    public final PrintTarget space() {
        writeText(" ");
        return this;
    }

    public final PrintTarget write(Token token) {
        switch (token) {
            case Token.NumberToken(var value, var style) -> writeText(NUMBER_FORMATS.get(style).format(value));
            case Token.KeywordToken keywordToken -> writeText(keywordToken.getValue());
            case Token.SimpleToken simpleToken -> writeText(simpleToken.getValue());
            case Token.WordToken(var value) -> printWord(value);
            case Token.StringToken(var value) -> printString(value, '"');
            case Token.ErrorToken(var error) -> {
                if (failOnError()) {
                    throw new PrettyPrintException(error);
                }
                writeText("\\error: ");
                printString(error, '\'');
                writeText("\\");
            }
            case Token.EofToken.EOF -> {}
            case Token.StringInterpolationToken(var content, var kind) -> {
                switch (kind) {
                    case START -> {
                        writeText("\"");
                        printStringContent(content, '"');
                        writeText("\\{");
                    }
                    case MIDDLE -> {
                        writeText("}");
                        printStringContent(content, '"');
                        writeText("\\{");
                    }
                    case END -> {
                        writeText("}");
                        printStringContent(content, '"');
                        writeText("\"");
                    }
                }
            }
        }
        return this;
    }

    public final PrintTarget writeCommentLine(String comment) {
        writeText("#");
        writeText(comment);
        return this;
    }


}

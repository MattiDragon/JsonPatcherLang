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

public abstract class PrintTarget {
    private static final Pattern SIMPLE_WORD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");
    private static final Pattern WEIRD_CHARACTER = Pattern.compile("\\p{C}");
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
            case Token.WordToken(var value) -> {
                if (PrintTarget.SIMPLE_WORD.matcher(value).matches() && !Token.KeywordToken.ALL.containsKey(value)) {
                    writeText(value);
                } else {
                    printString(value, '\'');
                }
            }
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

    private void printString(String contents, char quote) {
        writeText(String.valueOf(quote));
        printStringContent(contents, quote);
        writeText(String.valueOf(quote));
    }

    private void printStringContent(String contents, char quote) {
        contents.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '\n' -> writeText("\\n");
                case '\r' -> writeText("\\r");
                case '\t' -> writeText("\\t");
                case '\b' -> writeText("\\b");
                case '\\' -> writeText("\\\\");
                case '\0' -> writeText("\\0");
                default -> {
                    var charString = String.valueOf(Character.toChars(codePoint));
                    if (codePoint == quote) {
                        writeText("\\");
                        writeText(String.valueOf(quote));
                    } else if (WEIRD_CHARACTER.matcher(charString).matches()) {
                        printUnicodeEscape(codePoint);
                    } else {
                        writeText(charString);
                    }
                }
            }
        });
    }

    private void printUnicodeEscape(int codePoint) {
        if (codePoint <= 0xff) {
            writeText("\\x");
            writeText(Integer.toHexString(codePoint >> 4 & 0xf));
            writeText(Integer.toHexString(codePoint & 0xf));
        } else if (codePoint <= 0xffff) {
            writeText("\\u");
            writeText(Integer.toHexString(codePoint >> 12 & 0xf));
            writeText(Integer.toHexString(codePoint >> 8 & 0xf));
            writeText(Integer.toHexString(codePoint >> 4 & 0xf));
            writeText(Integer.toHexString(codePoint & 0xf));
        } else {
            printUnicodeEscape(Character.highSurrogate(codePoint));
            printUnicodeEscape(Character.lowSurrogate(codePoint));
        }
    }
}

package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.util.regex.Pattern;

public abstract class PrintTarget {
    private static final Pattern SIMPLE_WORD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");
    private static final Pattern WEIRD_CHARACTER = Pattern.compile("\\p{C}");

    public abstract PrintTarget pushIndent();

    public abstract PrintTarget popIndent();

    public abstract PrintTarget newLine();

    /**
     * Returns {@code true} if this target will ignore any future inputs.
     * Producers can use this to terminate early.
     */
    public boolean isClosed() {
        return false;
    }

    public abstract CharCounter newCharCounter();

    protected abstract void writeText(String text);

    protected abstract boolean failOnError();

    public final PrintTarget space() {
        writeText(" ");
        return this;
    }

    public final PrintTarget write(Token token) {
        switch (token) {
            case Token.NumberToken(var value) -> writeText(String.valueOf(value)); // TODO: ensure correct formatting
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
                    throw new IllegalStateException("Tried to pretty print error token: " + error);
                }
                writeText("\\error_token: ");
                printString(error, '\'');
                writeText(" \\");
            }
        }
        return this;
    }

    private void printString(String contents, char quote) {
        writeText(String.valueOf(quote));
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
        writeText(String.valueOf(quote));
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

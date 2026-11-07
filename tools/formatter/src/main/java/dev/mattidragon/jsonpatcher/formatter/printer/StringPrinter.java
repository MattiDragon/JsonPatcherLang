package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.util.regex.Pattern;

public abstract class StringPrinter {
    private static final Pattern WEIRD_CHARACTER = Pattern.compile("\\p{C}");
    private static final Pattern SIMPLE_WORD = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_$]*");

    protected abstract void writeText(String text);

    protected void printWord(String value) {
        if (SIMPLE_WORD.matcher(value).matches() && !Token.KeywordToken.ALL.containsKey(value)) {
            writeText(value);
        } else {
            printString(value, '\'');
        }
    }

    protected void printString(String contents, char quote) {
        writeText(String.valueOf(quote));
        printStringContent(contents, quote);
        writeText(String.valueOf(quote));
    }

    protected void printStringContent(String contents, char quote) {
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

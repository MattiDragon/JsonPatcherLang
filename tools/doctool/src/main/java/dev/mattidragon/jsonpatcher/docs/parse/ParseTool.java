package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

public class ParseTool {
    private final String content;
    private final SourcePos start;
    private int column;

    private @Nullable SourcePos pos1;
    private @Nullable SourcePos pos2;

    public ParseTool(String content, SourcePos start) {
        this.content = content;
        this.start = start;
        this.column = 0;
    }

    public void skipWhitespace() {
        while (hasNext() && peek() == ' ') next();
    }

    public String readWord() {
        if (!isWordChar(peek())) throw new DocParseException("Expected word, got '%s'".formatted(peek()), pos(0), DocParseError.Code.UNEXPECTED_CHARACTER);
        pos1 = pos(0);
        var builder = new StringBuilder();
        while (hasNext() && isWordChar(peek())) {
            builder.append(next());
        }
        pos2 = pos(-1);
        return builder.toString();
    }

    public String readString() {
        pos1 = pos(0);
        expect('"');
        var builder = new StringBuilder();
        while (hasNext() && peek() != '"') {
            var c = next();
            if (c == '\\') {
                builder.append(switch (next()) {
                    case '"' -> '"';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'b' -> '\b';
                    case '\\' -> '\\';
                    case 'x' -> parseUnicode(2);
                    case 'u' -> parseUnicode(4);
                    default -> throw new DocParseException("Illegal escape: '%s'".formatted(peek()), pos(0), DocParseError.Code.UNEXPECTED_CHARACTER);
                });
            } else {
                builder.append(c);
            }
        }
        pos2 = pos(0);
        expect('"');
        return builder.toString();
    }

    private char parseUnicode(int chars) {
        var string = new StringBuilder();
        for (var i = 0; i < chars; i++) {
            string.append(next());
        }
        return (char) Integer.parseInt(string.toString(), 16);
    }

    public static boolean isWordChar(char c) {
        return c >= 'a' && c <= 'z'
               || c >= 'A' && c <= 'Z'
               || c >= '0' && c <= '9'
               || c == '_';
    }

    public char peek() {
        if (!hasNext()) throw new DocParseException("Unexpected end of line", pos(0), DocParseError.Code.EOL);
        return content.charAt(column);
    }

    public char next() {
        if (!hasNext()) throw new DocParseException("Unexpected end of line", pos(0), DocParseError.Code.EOL);
        return content.charAt(column++);
    }

    public void expect(char expected) {
        var actual = next();
        if (actual != expected) {
            throw new DocParseException("Expected '%s', got '%s'".formatted(expected, actual), pos(0), DocParseError.Code.UNEXPECTED_CHARACTER);
        }
    }

    public void expectWord(String expected) {
        var actual = readWord();
        if (!actual.equals(expected)) {
            throw new DocParseException("Expected '%s', got '%s'".formatted(expected, actual), pos(0), DocParseError.Code.UNEXPECTED_CHARACTER);
        }
    }

    public void expectEol() {
        skipWhitespace();
        if (hasNext()) {
            throw new DocParseException("Expected end of line, got trailing data: '%s'".formatted(content.substring(column)), pos(0), DocParseError.Code.TRAILING_DATA);
        }
    }

    public boolean hasNext() {
        return column < content.length();
    }

    public SourcePos pos(int offset) {
        return start.offset(column + offset);
    }

    public SourceSpan span() {
        if (pos1 == null || pos2 == null) {
            throw new IllegalStateException("No token positions captured");
        }
        return new SourceSpan(pos1, pos2);
    }
}

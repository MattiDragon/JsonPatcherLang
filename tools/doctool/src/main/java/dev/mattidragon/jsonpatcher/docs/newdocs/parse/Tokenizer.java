package dev.mattidragon.jsonpatcher.docs.newdocs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

public class Tokenizer {
    private final String text;
    private final SourcePos firstPos;
    private int index = 0;
    private @Nullable SourcePos startPos;

    public Tokenizer(String text, SourcePos firstPos) {
        this.text = text;
        this.firstPos = firstPos;
    }

    DocToken next() {
        skipWhitespace();
        startPos = firstPos.offset(index);
        var c = nextChar();
        if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
            var word = new StringBuilder(String.valueOf(c));
            while (hasNextChar() && isWordChar(peekChar())) {
                word.append(nextChar());
            }
            return new DocToken.Name(word.toString());
        }

        if (c >= '0' && c <= '9') {
            var num = c - '0';
            while (hasNextChar() && peekChar() >= '0' && peekChar() <= '9') {
                num = num * 10 + nextChar() - '0';
            }
            return new DocToken.Number(num);
        }

        return switch (c) {
            case '(' -> DocToken.Symbol.BEGIN_PAREN;
            case ')' -> DocToken.Symbol.END_PAREN;
            case '[' -> DocToken.Symbol.BEGIN_SQUARE;
            case ']' -> DocToken.Symbol.END_SQUARE;
            case '{' -> DocToken.Symbol.BEGIN_CURLY;
            case '}' -> DocToken.Symbol.END_CURLY;
            case '<' -> DocToken.Symbol.BEGIN_ANGLE;
            case '>' -> DocToken.Symbol.END_ANGLE;
            case '|' -> DocToken.Symbol.BAR;
            case ':' -> DocToken.Symbol.COLON;
            case ',' -> DocToken.Symbol.COMMA;
            case '.' -> DocToken.Symbol.DOT;
            case '@' -> DocToken.Symbol.AT;
            case '!' -> DocToken.Symbol.BANG;
            case '&' -> DocToken.Symbol.AND;
            case '#' -> DocToken.Symbol.HASH;
            case '=' -> DocToken.Symbol.EQUAL;
            case '^' -> DocToken.Symbol.CARET;
            case '~' -> DocToken.Symbol.TILDE;
            case '-' -> {
                var c2 = nextChar();
                if (c2 == '>') yield DocToken.Symbol.ARROW;
                yield new DocToken.Error("Unexpected character after '-': '" + c + "'", "DOC-0", firstPos.offset(index).toSpan());
            }
            case '$' -> {
                var word = new StringBuilder();
                while (hasNextChar() && isWordChar(peekChar())) {
                    word.append(nextChar());
                }
                if (word.isEmpty()) {
                    yield new DocToken.Error("Expected name after '$'", "DOC-0", firstPos.offset(index).toSpan());
                }
                yield new DocToken.VarName(word.toString());
            }
            default -> new DocToken.Error("Unexpected character: '" + c + "'", "DOC-0", firstPos.offset(index).toSpan());
        };
    }

    DocToken peek() {
        var storedPos = startPos;
        var storedIndex = index;
        var token = next();
        startPos = storedPos;
        index = storedIndex;
        return token;
    }

    @VisibleForTesting
    public boolean hasNext() {
        skipWhitespace();
        return hasNextChar();
    }

    SourceSpan lastPos() {
        if (startPos == null) throw new IllegalStateException("No position recorded");
        return new SourceSpan(startPos, firstPos.offset(index - 1));
    }

    SourceSpan nextPos() {
        var storedPos = startPos;
        var storedIndex = index;
        next();
        var pos = lastPos();
        startPos = storedPos;
        index = storedIndex;
        return pos;
    }

    private char nextChar() {
        return text.charAt(index++);
    }

    private char peekChar() {
        return text.charAt(index);
    }

    private boolean hasNextChar() {
        return index < text.length();
    }

    private boolean isWordChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
    }

    private void skipWhitespace() {
        while (index < text.length() && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) {
            index++;
        }
    }
}

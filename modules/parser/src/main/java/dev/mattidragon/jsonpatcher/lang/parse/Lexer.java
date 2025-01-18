package dev.mattidragon.jsonpatcher.lang.parse;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    public static final int TAB_WIDTH = 4;
    private final SourceFile file;
    private final String program;
    private final DiagnosticsBuilder diagnostics;
    private final List<PositionedToken> tokens = new ArrayList<>();
    private int current = 0;
    private int currentLine = 1;
    private int currentColumn = 1;
    private CommentHandler commentHandler = CommentHandler.EMPTY;

    private Lexer(String program, String filename, DiagnosticsBuilder diagnostics) {
        this.program = program;
        this.diagnostics = diagnostics;
        this.file = new SourceFile(filename, program);
    }

    private Result lex() {
        try {
            while (hasNext()) {
                var c = next();
                if (c == ' ' || c == '\r' || c == '\n' || c == '\t') {
                    continue;
                }

                if (c == '"' || c == '\'') {
                    readString(c);
                } else if (c == '#') {
                    skipComment();
                } else {
                    if (c >= '0' && c <= '9') readNumber(c);
                    else if (TokenTree.isStart(c)) readSimpleToken(c);
                    else if (isWordStartChar(c)) readWord(c);
                    else {
                        var token = new Token.ErrorToken("Unexpected character: %c (0x%x)".formatted(c, (int) c));
                        var from = new SourcePos(file, currentLine, currentColumn - 1);
                        var to = new SourcePos(file, currentLine, currentColumn - 1);
                        SourceSpan pos = new SourceSpan(from, to);
                        diagnostics.addDiagnostic(new LexError(token.error(), pos.from(), LexError.UNEXPECTED_CHAR));
                    }
                }
            }
        } catch (EofMarker e) {
            diagnostics.addDiagnostic(error("Unexpected end of file", LexError.EOF));
        }

        return new Result(tokens);
    }

    public static Result lex(String program, String filename, DiagnosticsBuilder diagnostics) {
        return new Lexer(program, filename, diagnostics).lex();
    }

    public static Result lex(String program, String filename, DiagnosticsBuilder diagnostics, CommentHandler commentHandler) {
        var lexer = new Lexer(program, filename, diagnostics);
        lexer.commentHandler = commentHandler;
        return lexer.lex();
    }

    private void skipComment() {
        var comments = new ArrayList<CommentHandler.Comment>();
        var builder = new StringBuilder();

        gatherBlock:
        while (hasNext()) {
            var begin = new SourcePos(file, currentLine, currentColumn);
            
            while (hasNext() && peek() != '\n') {
                builder.append(next());
            }
            comments.add(new CommentHandler.Comment(builder.toString(), begin));
            builder.delete(0, builder.length());
            
            if (hasNext() && peek() == '\n') {
                next();
            }
            
            while (hasNext()) {
                var c = peek();
                if (c == ' ' || c == '\t') {
                    next();
                } else if (c == '#') {
                    next();
                    break;
                } else {
                    break gatherBlock;
                }
            }
        }
        commentHandler.acceptBlock(comments);
    }

    private void readSimpleToken(char c) {
        var success = TokenTree.parse(this, c);
        if (!success) {
            diagnostics.addDiagnostic(error("Unable to parse token", 1, LexError.BROKEN_SIMPLE_TOKEN));
        }
    }

    // TODO: Find a better way to deal with EOF in number parsing
    private void readNumber(char c) {
        var string = new StringBuilder();
        var beginPos = currentColumn - 1;
        string.append(c);
        parse: {
            if (!hasNext()) break parse;
            for (c = peek(); c >= '0' && c <= '9'; c = peek()) {
                string.append(next());
                if (!hasNext()) break parse;
            }

            if (!hasNext()) break parse;
            if (peek() == '.') string.append(next());

            if (!hasNext()) break parse;
            for (c = peek(); c >= '0' && c <= '9'; c = peek()) {
                string.append(next());
                if (!hasNext()) break parse;
            }
        }

        var token = new Token.NumberToken(Double.parseDouble(string.toString()));
        addParsedToken(token, currentColumn - beginPos);
    }

    private void readWord(char c) {
        var string = new StringBuilder();
        var length = 1;
        string.append(c);
        while (hasNext() && isWordChar(peek())) {
            string.append(next());
            length++;
        }

        if (Token.KeywordToken.ALL.containsKey(string.toString())) {
            addParsedToken(Token.KeywordToken.ALL.get(string.toString()), length);
        } else {
            addParsedToken(new Token.WordToken(string.toString()), length);
        }
    }

    private boolean isWordStartChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isWordChar(char c) {
        return isWordStartChar(c) || (c >= '0' && c <= '9') || c == '$';
    }

    private void readString(char begin) {
        var string = new StringBuilder();
        var beginPos = currentColumn - 1;

        for (char c = next(); c != begin; c = next()) {
            switch (c) {
                case '\\' -> {
                    var escaped = next();
                    switch (escaped) {
                        case 'n' -> string.append('\n');
                        case 'r' -> string.append('\r');
                        case 't' -> string.append('\t');
                        case 'b' -> string.append('\b');
                        case '"' -> string.append('"');
                        case '\'' -> string.append('\'');
                        case '\\' -> string.append('\\');
                        case '0' -> string.append('\0');
                        case 'x' -> string.append(readUnicodeEscape(2));
                        case 'u' -> string.append(readUnicodeEscape(4));
                        default -> diagnostics.addDiagnostic(error("Unknown escape sequence: \\%c".formatted(escaped), 1, LexError.ILLEGAL_ESCAPE));
                    }
                }
                case '\n', '\r' -> diagnostics.addDiagnostic(error("Multiline strings aren't supported. Did you forget a quote?", LexError.MULTILINE_STRING));
                default -> string.append(c);
            }
        }

        var token = begin == '"' ? new Token.StringToken(string.toString()) : new Token.WordToken(string.toString());
        addParsedToken(token, currentColumn - beginPos);
    }

    private char readUnicodeEscape(int length) {
        char value = 0;
        for (var i = 0; i < length; i++) {
            var c = next();
            value *= 16;
            if (c >= '0' && c <= '9') value += (char) (c - '0');
            else if (c >= 'a' && c <= 'f') value += (char) (c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') value += (char) (c - 'A' + 10);
            else diagnostics.addDiagnostic(error("Invalid character in unicode escape: %c".formatted(c), 1, LexError.ILLEGAL_ESCAPE));
        }
        return value;
    }

    public boolean hasNext() {
        return current < program.length();
    }

    public char peek() {
        if (!hasNext()) throw new EofMarker();
        return program.charAt(current);
    }

    public char next() {
        if (!hasNext()) throw new EofMarker();
        var c = program.charAt(current++);
        if (c == '\n') {
            currentLine++;
            currentColumn = 0;
        } if (c == '\t') {
            currentColumn += TAB_WIDTH;
        } else {
            currentColumn++;
        }

        return c;
    }

    public void addParsedToken(Token token, int length) {
        var from = new SourcePos(file, currentLine, currentColumn - length);
        var to = new SourcePos(file, currentLine, currentColumn - 1);
        SourceSpan pos = new SourceSpan(from, to);
        if (token instanceof Token.ErrorToken(var error)) {
            diagnostics.addDiagnostic(new LexError(error, pos.from(), LexError.EOF));
        } else {
            tokens.add(new PositionedToken(pos, token));
        }
    }

    public Position savePos() {
        return new Position(current, currentLine, currentColumn);
    }

    public void loadPos(Position pos) {
        current = pos.current;
        currentLine = pos.currentLine;
        currentColumn = pos.currentColumn;
    }
    
    public Diagnostic error(String message, String id) {
        return error(message, 0, id);
    }

    public Diagnostic error(String message, int offset, String id) {
        return new LexError(message, new SourcePos(file, currentLine, currentColumn - offset), id);
    }
    
    static class EofMarker extends RuntimeException {
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public static final class LexError implements Diagnostic {
        private static final String EOF = "LEX-0";
        private static final String MULTILINE_STRING = "LEX-1";
        private static final String ILLEGAL_ESCAPE = "LEX-2";
        private static final String BROKEN_SIMPLE_TOKEN = "LEX-3";
        private static final String UNEXPECTED_CHAR = "LEX-4";

        private final SourcePos pos;
        private final String message;
        private final String id;
        
        LexError(String message, SourcePos pos, String id) {
            this.pos = pos;
            this.message = message;
            this.id = id;
        }

        @Override
        public SourceSpan pos() {
            return new SourceSpan(pos, pos);
        }

        @Override
        public @Nullable ProgramNode node() {
            return null;
        }

        @Override
        public String message() {
            return message;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Kind kind() {
            return Kind.ERROR;
        }
    }

    public record Result(List<PositionedToken> tokens) {
    }

    public record Position(int current, int currentLine, int currentColumn) {}
}

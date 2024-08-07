package io.github.mattidragon.jsonpatcher.lang.parse;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    public static final int TAB_WIDTH = 4;
    private final LangConfig config;
    private final SourceFile file;
    private final String program;
    private final List<PositionedToken> tokens = new ArrayList<>();
    private final List<Lexer.LexException> errors = new ArrayList<>();
    private int current = 0;
    private int currentLine = 1;
    private int currentColumn = 1;
    private CommentHandler commentHandler = CommentHandler.EMPTY;

    private Lexer(LangConfig config, String program, String filename) {
        this.config = config;
        this.program = program;
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
                        addParsedToken(new Token.ErrorToken("Unexpected character: %c (0x%x)".formatted(c, (int) c)), 1);
                    }
                }
            }
        } catch (Lexer.LexException e) {
            errors.add(e);
        }

        return new Result(tokens, errors);
    }

    public static Result lex(LangConfig config, String program, String filename) {
        return new Lexer(config, program, filename).lex();
    }

    public static Result lex(LangConfig config, String program, String filename, CommentHandler commentHandler) {
        var lexer = new Lexer(config, program, filename);
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
            errors.add(error("Unable to parse token", 1));
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
                        default -> errors.add(error("Unknown escape sequence: \\%c".formatted(escaped), 1));
                    }
                }
                case '\n', '\r' -> errors.add(error("Multiline strings aren't supported. Did you forget a quote?"));
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
            else errors.add(error("Invalid character in unicode escape: %c".formatted(c), 1));
        }
        return value;
    }

    public boolean hasNext() {
        return current < program.length();
    }

    public char peek() {
        if (!hasNext()) throw error("Unexpected end of file");
        return program.charAt(current);
    }

    public char next() {
        if (!hasNext()) throw error("Unexpected end of file");
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
        if (token instanceof Token.ErrorToken errorToken) {
            errors.add(new LexException(config, errorToken.error(), pos.from()));
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
    
    public LexException error(String message) {
        return error(message, 0);
    }

    public LexException error(String message, int offset) {
        return new LexException(config, message, new SourcePos(file, currentLine, currentColumn - offset));
    }

    public static class LexException extends PositionedException {
        private final SourcePos pos;
        
        public LexException(LangConfig config, String message, SourcePos pos) {
            super(config, message);
            this.pos = pos;
        }

        @Override
        protected String getBaseMessage() {
            return "Error while parsing tokens";
        }

        @Override
        @Nullable
        public SourceSpan getPos() {
            return new SourceSpan(pos, pos);
        }
    }

    public record Result(List<PositionedToken> tokens, List<LexException> errors) {
    }

    public record Position(int current, int currentLine, int currentColumn) {}
}

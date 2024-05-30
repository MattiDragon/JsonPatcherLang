package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.docs.data.DocType;

import java.util.ArrayList;

public class TypeParser {
    private final String file;
    private final String text;
    private int index = 0;

    public TypeParser(String file, String text) {
        this.file = file;
        this.text = text;
    }

    public static DocType parse(String text, String currentFile) {
        var parser = new TypeParser(currentFile, text);
        return parser.type();
    }
    
    private DocType type() {
        var types = new ArrayList<DocType>();
        types.add(atom());
        skipWhitespace();
        
        while (hasNext() && peek() == '|') {
            next();
            types.add(atom());
            skipWhitespace();
        }
        
        if (types.size() == 1) return types.getFirst();
        else return new DocType.Union(types);
    }
    
    private DocType atom() {
        skipWhitespace();
        return switch ((Character) peek()) {
            case '(' -> function();
            case '[' -> array();
            case '{' -> object();
            case Character c when isNameChar(c) -> name();
            case Character c -> throw new DocParseException("Unexpected character in type expression: '%s' (file: %s)".formatted(c, file));
        };
    }

    private static boolean isNameChar(Character c) {
        return c >= 'a' && c <= 'z'
               || c >= 'A' && c <= 'Z'
               || c >= '0' && c <= '9'
               || c == '_';
    }

    private DocType function() {
        expect('(');
        skipWhitespace();
        
        var args = new ArrayList<DocType.Function.Argument>();
        argLoop:
        while (hasNext() && isNameChar(peek())) {
            var name = readString();
            var optional = false;
            var varargs = false;
            skipWhitespace();
            if (hasNext() && peek() == '?') {
                optional = true;
                next();
            } else if (hasNext() && peek() == '*') {
                varargs = true;
                next();
            }
            skipWhitespace();
            expect(':');
            var type = type();
            args.add(new DocType.Function.Argument(name, type, optional, varargs));
            skipWhitespace();
            if (!hasNext()) throw new DocParseException("EOL in function arguments (file: %s)".formatted(file));
            switch (peek()) {
                case ',' -> {
                    next();
                    skipWhitespace();
                }
                case ')' -> {
                    break argLoop;
                }
                default -> throw new DocParseException("Unexpected char in function arguments: '%s' (file: %s)".formatted(peek(), file));
            }
        }
        expect(')');
        skipWhitespace();
        expect('-');
        expect('>');
        var returnType = type();
        return new DocType.Function(returnType, args);
    }
    
    private DocType array() {
        expect('[');
        skipWhitespace();
        var type = type();
        skipWhitespace();
        expect(']');
        return new DocType.Array(type);
    }
    
    private DocType object() {
        expect('{');
        skipWhitespace();
        var type = type();
        skipWhitespace();
        expect('}');
        return new DocType.Object(type);
    }
    
    private DocType name() {
        return switch (readString()) {
            case "any" -> DocType.Special.ANY;
            case "number" -> DocType.Special.NUMBER;
            case "string" -> DocType.Special.STRING;
            case "boolean" -> DocType.Special.BOOLEAN;
            case "array" -> DocType.Special.ARRAY;
            case "object" -> DocType.Special.OBJECT;
            case "function" -> DocType.Special.FUNCTION;
            case "null" -> DocType.Special.NULL;
            case String other -> new DocType.Name(other);
        };
    }

    private String readString() {
        var name = new StringBuilder();
        while (hasNext() && isNameChar(peek())) {
            name.append(next());
        }
        return name.toString();
    }

    private void skipWhitespace() {
        while (hasNext() && peek() == ' ') {
            next();
        }
    }
    
    private boolean hasNext() {
        return index < text.length();
    }
    
    private char peek() {
        return text.charAt(index);
    }
    
    private char next() {
        return text.charAt(index++);
    }
    
    private void expect(char c) {
        if (!hasNext() || next() != c) throw new DocParseException("Expected '%s' (file: %s)".formatted(c, file));
    }
}

package io.github.mattidragon.jsonpatcher.docs.parse;

import io.github.mattidragon.jsonpatcher.docs.data.DocType;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;

import java.util.ArrayList;

public class TypeParser {
    private final SourcePos pos;
    private final String text;
    private int index = 0;

    public TypeParser(SourcePos pos, String text) {
        this.pos = pos;
        this.text = text;
    }

    public static DocType parse(String text, SourcePos pos) {
        var parser = new TypeParser(pos, text);
        return parser.type();
    }
    
    private DocType type() {
        var types = new ArrayList<DocType>();
        var separators = new ArrayList<SourcePos>();
        types.add(atom());
        skipWhitespace();
        
        while (hasNext() && peek() == '|') {
            next();
            separators.add(pos());
            types.add(atom());
            skipWhitespace();
        }
        
        if (types.size() == 1) return types.getFirst();
        else return new DocType.Union(types, separators);
    }
    
    private DocType atom() {
        skipWhitespace();
        return switch ((Character) peek()) {
            case '(' -> function();
            case '[' -> array();
            case '{' -> object();
            case Character c when isNameChar(c) -> name();
            case Character c -> throw new DocParseException("Unexpected character in type expression: '%s'".formatted(c), pos(0));
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
            if (!hasNext()) throw new DocParseException("EOL in function arguments", pos());
            switch (peek()) {
                case ',' -> {
                    next();
                    skipWhitespace();
                }
                case ')' -> {
                    break argLoop;
                }
                default -> throw new DocParseException("Unexpected char in function arguments: '%s'".formatted(peek()), pos());
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
        var start = pos(0);
        var name = readString();
        var end = pos();
        var pos = new SourceSpan(start, end);
        return switch (name) {
            case "any" -> new DocType.Special(DocType.SpecialKind.ANY, pos);
            case "number" -> new DocType.Special(DocType.SpecialKind.NUMBER, pos);
            case "string" -> new DocType.Special(DocType.SpecialKind.STRING, pos);
            case "boolean" -> new DocType.Special(DocType.SpecialKind.BOOLEAN, pos);
            case "array" -> new DocType.Special(DocType.SpecialKind.ARRAY, pos);
            case "object" -> new DocType.Special(DocType.SpecialKind.OBJECT, pos);
            case "function" -> new DocType.Special(DocType.SpecialKind.FUNCTION, pos);
            case "null" -> new DocType.Special(DocType.SpecialKind.NULL, pos);
            case String other -> new DocType.Name(other, pos);
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
        if (!hasNext() || next() != c) throw new DocParseException("Expected '%s'".formatted(c), pos());
    }
    
    private SourcePos pos() {
        return pos(-1);
    }
    
    private SourcePos pos(int offset) {
        return pos.offset(index + offset);
    }
}

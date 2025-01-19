package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.docs.data.DocType;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.ArrayList;

public class TypeParser {
    private final ParseTool parseTool;

    public TypeParser(ParseTool parseTool) {
        this.parseTool = parseTool;
    }

    public static DocType parse(String text, SourcePos pos) {
        return parse(new ParseTool(text, pos));
    }

    public static DocType parse(ParseTool parseTool) {
        var parser = new TypeParser(parseTool);
        return parser.type();
    }

    private DocType type() {
        var types = new ArrayList<DocType>();
        var separators = new ArrayList<SourcePos>();
        types.add(atom());
        parseTool.skipWhitespace();

        while (parseTool.hasNext() && parseTool.peek() == '|') {
            parseTool.next();
            separators.add(parseTool.pos(-1));
            types.add(atom());
            parseTool.skipWhitespace();
        }
        
        if (types.size() == 1) return types.getFirst();
        else return new DocType.Union(types, separators);
    }
    
    private DocType atom() {
        parseTool.skipWhitespace();
        return switch ((Character) parseTool.peek()) {
            case '(' -> function();
            case '[' -> array();
            case '{' -> object();
            case Character c when ParseTool.isWordChar(c) -> name();
            case Character c -> throw new DocParseException("Unexpected character in type expression: '%s'".formatted(c), parseTool.pos(0), DocParseError.Code.UNEXPECTED_CHARACTER);
        };
    }

    private DocType function() {
        var functionOperatorPoses = new ArrayList<SourceSpan>();
        parseTool.expect('(');
        functionOperatorPoses.add(parseTool.pos(-1).toSpan());
        parseTool.skipWhitespace();

        var args = new ArrayList<DocType.Function.Argument>();
        argLoop:
        while (true) {
            if (!(parseTool.hasNext() && ParseTool.isWordChar(parseTool.peek()))) break;
            var nameStart = parseTool.pos(0);
            var name = readString();
            var nameEnd = parseTool.pos(-1);
            var operatorPoses = new ArrayList<SourcePos>();
            var optional = false;
            var varargs = false;
            parseTool.skipWhitespace();
            if (parseTool.hasNext() && parseTool.peek() == '?') {
                optional = true;
                parseTool.next();
                operatorPoses.add(parseTool.pos(-1));
            } else {
                if (parseTool.hasNext() && parseTool.peek() == '*') {
                    varargs = true;
                    parseTool.next();
                    operatorPoses.add(parseTool.pos(-1));
                }
            }
            parseTool.skipWhitespace();
            parseTool.expect(':');
            operatorPoses.add(parseTool.pos(-1));
            var type = type();
            args.add(new DocType.Function.Argument(name, type, optional, varargs, new SourceSpan(nameStart, nameEnd), operatorPoses));
            parseTool.skipWhitespace();
            if (!parseTool.hasNext()) throw new DocParseException("EOL in function arguments", parseTool.pos(-1), DocParseError.Code.EOL);
            switch (parseTool.peek()) {
                case ',' -> {
                    parseTool.next();
                    operatorPoses.add(parseTool.pos(-1));
                    parseTool.skipWhitespace();
                }
                case ')' -> {
                    break argLoop;
                }
                default -> throw new DocParseException("Unexpected char in function arguments: '%s'".formatted(parseTool.peek()), parseTool.pos(-1), DocParseError.Code.UNEXPECTED_CHARACTER);
            }
        }
        parseTool.expect(')');
        functionOperatorPoses.add(parseTool.pos(-1).toSpan());
        parseTool.skipWhitespace();
        parseTool.expect('-');
        parseTool.expect('>');
        functionOperatorPoses.add(new SourceSpan(parseTool.pos(-2), parseTool.pos(-1)));
        var returnType = type();
        return new DocType.Function(returnType, args, functionOperatorPoses);
    }
    
    private DocType array() {
        parseTool.expect('[');
        parseTool.skipWhitespace();
        var type = type();
        parseTool.skipWhitespace();
        parseTool.expect(']');
        return new DocType.Array(type);
    }
    
    private DocType object() {
        parseTool.expect('{');
        parseTool.skipWhitespace();
        var type = type();
        parseTool.skipWhitespace();
        parseTool.expect('}');
        return new DocType.Object(type);
    }
    
    private DocType name() {
        var name = readString();
        var pos = parseTool.span();
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
        return parseTool.readWord();
    }

}

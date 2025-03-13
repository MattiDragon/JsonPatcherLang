package dev.mattidragon.jsonpatcher.lang.parse.metadata;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.util.ArrayList;
import java.util.HashMap;

class JsonParser {
    private final Parser parser;

    public JsonParser(Parser parser) {
        this.parser = parser;
    }

    public MetadataElement parse() {
        var token = parser.next();
        var pos = token.pos();
        return switch (token.token()) {
            case Token.SimpleToken.BEGIN_CURLY -> parseObject();
            case Token.SimpleToken.BEGIN_SQUARE -> parseArray();
            case Token.StringToken(var value) ->
                    parser.setMetadata(new MetadataString(value), MetadataKey.FULL_POS, pos);
            case Token.SimpleToken.MINUS -> parser.setMetadata(
                    new MetadataNumber(-parser.expectNumber().value()),
                    MetadataKey.FULL_POS,
                    new SourceSpan(pos.from(), parser.previous().to()));
            case Token.NumberToken(var value) ->
                    parser.setMetadata(new MetadataNumber(value), MetadataKey.FULL_POS, pos);
            case Token.KeywordToken.TRUE -> parser.setMetadata(new MetadataBoolean(true), MetadataKey.FULL_POS, pos);
            case Token.KeywordToken.FALSE -> parser.setMetadata(new MetadataBoolean(false), MetadataKey.FULL_POS, pos);
            case Token.KeywordToken.NULL -> parser.setMetadata(new MetadataNull(), MetadataKey.FULL_POS, pos);
            default -> throw new Parser.ParseException(new Parser.ParseDiagnostic(token.pos(), null, "Unexpected token in json: " + token.token().explain(), Parser.ParseDiagnostic.Code.UNEXPECTED_TOKEN));
        };
    }

    private MetadataObject parseObject() {
        var startPos = parser.previous().pos();
        var map = new HashMap<String, MetadataElement>();
        while (parser.hasNext() && parser.peek().token() != Token.SimpleToken.END_CURLY) {
            var key = parser.expectString().value();
            parser.expect(Token.SimpleToken.COLON);
            var value = parse();

            map.put(key, value);

            PositionedToken positionedToken = parser.peek();
            if (positionedToken.token() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_CURLY);
        var object = new MetadataObject(map);
        parser.setMetadata(object, MetadataKey.FULL_POS, new SourceSpan(startPos.from(), parser.previous().to()));
        return object;
    }

    private MetadataArray parseArray() {
        var startPos = parser.previous().pos();
        var list = new ArrayList<MetadataElement>();
        while (parser.hasNext() && parser.peek().token() != Token.SimpleToken.END_SQUARE) {
            list.add(parse());

            PositionedToken positionedToken = parser.peek();
            if (positionedToken.token() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_SQUARE);
        var array = new MetadataArray(list);
        parser.setMetadata(array, MetadataKey.FULL_POS, new SourceSpan(startPos.from(), parser.previous().to()));
        return array;
    }
}

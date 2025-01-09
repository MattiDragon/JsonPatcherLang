package dev.mattidragon.jsonpatcher.lang.parse.metadata;

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
        return switch (token.token()) {
            case Token.SimpleToken.BEGIN_CURLY -> parseObject();
            case Token.SimpleToken.BEGIN_SQUARE -> parseArray();
            case Token.StringToken(var value) -> new MetadataString(value);
            case Token.SimpleToken.MINUS -> new MetadataNumber(-parser.expectNumber().value());
            case Token.NumberToken(var value) -> new MetadataNumber(value);
            case Token.KeywordToken.TRUE -> new MetadataBoolean(true);
            case Token.KeywordToken.FALSE -> new MetadataBoolean(false);
            case Token.KeywordToken.NULL -> MetadataNull.INSTANCE;
            default -> throw parser.new ParseException("Unexpected token in json: " + token.token(), token.pos());
        };
    }

    private MetadataObject parseObject() {
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
        return new MetadataObject(map);
    }

    private MetadataArray parseArray() {
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
        return new MetadataArray(list);
    }
}

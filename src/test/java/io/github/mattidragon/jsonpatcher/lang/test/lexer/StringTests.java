package io.github.mattidragon.jsonpatcher.lang.test.lexer;

import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import io.github.mattidragon.jsonpatcher.lang.parse.Token;
import io.github.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringTests {
    @Test
    public void testSimpleUnicodeEscape() {
        var program = """
                "\\u0041"
                """;
        var tokens = Lexer.lex(TestUtils.CONFIG, program, "test file").tokens();
        assertEquals(1, tokens.size(), "Expected 1 token");
        PositionedToken positionedToken = tokens.getFirst();
        var token = positionedToken.token();
        assertInstanceOf(Token.StringToken.class, token, "Expected StringToken");
        assertEquals("A", ((Token.StringToken) token).value(), "Expected A");
    }

    @Test
    public void testInvalidUnicodeEscape() {
        var program = """
                "\\u0gggg"
                """;
        var result = Lexer.lex(TestUtils.CONFIG, program, "test file");
        assertFalse(result.errors().isEmpty(), "Expected error from invalid escape");
    }

    @Test
    public void testKeywordDetection() {
        var program = """
                true
                """;
        var tokens = Lexer.lex(TestUtils.CONFIG, program, "test file").tokens();
        assertEquals(1, tokens.size(), "Expected 1 token");
        PositionedToken positionedToken = tokens.getFirst();
        var token = positionedToken.token();
        assertInstanceOf(Token.KeywordToken.class, token, "Expected KeywordToken");
        assertEquals(Token.KeywordToken.TRUE, token, "Expected true");
    }

    @Test
    public void testKeywordEscaping() {
        var program = """
                'true'
                """;
        var tokens = Lexer.lex(TestUtils.CONFIG, program, "test file").tokens();
        assertEquals(1, tokens.size(), "Expected 1 token");
        PositionedToken positionedToken = tokens.getFirst();
        var token = positionedToken.token();
        assertInstanceOf(Token.WordToken.class, token, "Expected WordToken");
        assertEquals("true", ((Token.WordToken) token).value(), "Expected true");
    }
}

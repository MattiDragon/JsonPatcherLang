package dev.mattidragon.jsonpatcher.lang.parser.test.lexer;

import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StringTests {
    @Test
    public void testSimpleUnicodeEscape() {
        var program = """
                "\\u0041"
                """;
        var diagnostics = new DiagnosticsBuilder();
        var tokens = Lexer.lex(program, "test file", diagnostics).tokens();

        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        assertEquals(1, tokens.size(), "Expected 1 token");

        var token = tokens.getFirst().token();
        assertInstanceOf(Token.StringToken.class, token, "Expected StringToken");
        assertEquals("A", ((Token.StringToken) token).value(), "Expected A");
    }

    @Test
    public void testInvalidUnicodeEscape() {
        var program = """
                "\\u0gggg"
                """;
        var diagnostics = new DiagnosticsBuilder();
        Lexer.lex(program, "test file", diagnostics);
        assertFalse(diagnostics.build().errors().isEmpty(), "Expected error from invalid escape");
    }

    @Test
    public void testKeywordDetection() {
        var program = """
                true
                """;
        var diagnostics = new DiagnosticsBuilder();
        var tokens = Lexer.lex(program, "test file", diagnostics).tokens();

        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        assertEquals(1, tokens.size(), "Expected 1 token");

        var token = tokens.getFirst().token();
        assertInstanceOf(Token.KeywordToken.class, token, "Expected KeywordToken");
        assertEquals(Token.KeywordToken.TRUE, token, "Expected true");
    }

    @Test
    public void testKeywordEscaping() {
        var program = """
                'true'
                """;
        var diagnostics = new DiagnosticsBuilder();
        var tokens = Lexer.lex(program, "test file", diagnostics).tokens();

        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        assertEquals(1, tokens.size(), "Expected 1 token");

        var token = tokens.getFirst().token();
        assertInstanceOf(Token.WordToken.class, token, "Expected WordToken");
        assertEquals("true", ((Token.WordToken) token).value(), "Expected true");
    }
}

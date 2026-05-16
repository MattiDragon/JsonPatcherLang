package dev.mattidragon.jsonpatcher.lang.parser.test.lexer;

import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StringInterpolationTests {
    @Test
    public void testBasicInterpolation() {
        var program = """
                "aaa\\{}bb\\{{}}\\{}}"
                """;
        var diagnostics = new DiagnosticsBuilder();
        var tokens = Lexer.lex(program, "test file", diagnostics).tokens();

        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        assertIterableEquals(List.of(
                new Token.StringInterpolationToken("aaa", Token.StringInterpolationToken.Kind.START),
                new Token.StringInterpolationToken("bb", Token.StringInterpolationToken.Kind.MIDDLE),
                Token.SimpleToken.BEGIN_CURLY,
                Token.SimpleToken.END_CURLY,
                new Token.StringInterpolationToken("", Token.StringInterpolationToken.Kind.MIDDLE),
                new Token.StringInterpolationToken("}", Token.StringInterpolationToken.Kind.END)
        ), tokens.stream().map(PositionedToken::token).toList());
    }
}

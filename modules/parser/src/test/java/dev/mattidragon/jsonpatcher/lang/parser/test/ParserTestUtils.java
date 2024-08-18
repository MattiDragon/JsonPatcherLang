package dev.mattidragon.jsonpatcher.lang.parser.test;

import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.ast.Program;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import org.junit.jupiter.api.Assertions;

import java.util.Collections;

public class ParserTestUtils {
    public static Expression parseExpression(String code) {
        var lex = Lexer.lex(TestUtils.CONFIG, code, "test program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        return Parser.parseExpression(TestUtils.CONFIG, lex.tokens());
    }
    
    public static Program parseProgram(String code) {
        var lex = Lexer.lex(TestUtils.CONFIG, code, "test program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        var parse = Parser.parse(TestUtils.CONFIG, lex.tokens());
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, parse.errors(), "Failed to parse");
        return parse.program();
    }
}

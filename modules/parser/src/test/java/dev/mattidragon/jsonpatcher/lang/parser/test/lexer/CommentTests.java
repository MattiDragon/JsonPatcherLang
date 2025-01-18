package dev.mattidragon.jsonpatcher.lang.parser.test.lexer;

import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class CommentTests {
    @Test
    public void basicComments() {
        var code = """
                #Comment
                # x2
                
                # Comment after empty line
                #
                # ^ Empty comment
                
                # ## # comment with #
                
                # comment at eof
                """;
        var diagnostics = new DiagnosticsBuilder();
        var result = Lexer.lex(code, "test file", diagnostics);
        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        Assertions.assertTrue(result.tokens().isEmpty(), "No tokens should be emitted");
    }
    
    @Test
    public void commendHandler() {
        var code = """
                #Comment
                # x2
                
                # Comment after empty line
                #
                # ^ Empty comment
                
                # ## # comment with #
                
                # comment at eof
                """;
        var diagnostics = new DiagnosticsBuilder();
        var expected = List.of(
                List.of("Comment", " x2"),
                List.of(" Comment after empty line", "", " ^ Empty comment"),
                List.of(" ## # comment with #"),
                List.of(" comment at eof")
        );
        var blocks = new ArrayList<List<String>>();
        Lexer.lex(code, "test file", diagnostics, block -> blocks.add(block.stream().map(CommentHandler.Comment::text).toList()));

        TestUtils.checkDiagnostics(diagnostics.build(), "Lexer error", false);
        Assertions.assertIterableEquals(
                expected,
                blocks,
                "Comment blocks should match"
        );
    }
}

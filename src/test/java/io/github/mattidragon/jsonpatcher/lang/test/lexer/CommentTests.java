package io.github.mattidragon.jsonpatcher.lang.test.lexer;

import io.github.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
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
        var result = Lexer.lex(code, "test file");
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
        var expected = List.of(
                List.of("Comment", " x2"),
                List.of(" Comment after empty line", "", " ^ Empty comment"),
                List.of(" ## # comment with #"),
                List.of(" comment at eof")
        );
        var blocks = new ArrayList<List<String>>();
        Lexer.lex(code, "test file", block -> blocks.add(block.stream().map(CommentHandler.Comment::text).toList()));
        
        Assertions.assertIterableEquals(
                expected,
                blocks,
                "Comment blocks should match"
        );
    }
}

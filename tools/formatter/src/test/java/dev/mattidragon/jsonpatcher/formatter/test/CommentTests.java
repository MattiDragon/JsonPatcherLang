package dev.mattidragon.jsonpatcher.formatter.test;

import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrinter;
import dev.mattidragon.jsonpatcher.formatter.printer.ProgramPrinter;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class CommentTests {
    @Test
    public void testEachTokenComment() {
        testComments("""
                #a
                f
                #b
                (
                #c
                )
                #d
                ;
                """, List.of("a", "b", "c", "d"));
    }

    private void testComments(String programCode, List<String> comments) {
        var parse = TestUtils.parseFull(programCode);

        var printer = new PrettyPrinter(FormatValidator.OPTIONS, parse.treeMetadata());
        ProgramPrinter.prettyPrint(parse.program(), parse.metadata(), printer);
        var code = printer.getOutput();

        var failed = new ArrayList<String>();
        for (var comment : comments) {
            if (!code.contains("#" + comment)) {
                failed.add(comment);
            }
        }

        if (!failed.isEmpty()) {
            AssertionFailureBuilder.assertionFailure()
                    .message("Missing comments in formatted code")
                    .reason("""
                            Formatted code:
                            %s
                            
                            Missing comments:
                            %s
                            """.formatted(code, String.join("\n", failed)))
                    .buildAndThrow();
        }
    }
}

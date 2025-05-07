package dev.mattidragon.jsonpatcher.docs.newdocs.test.parse;

import dev.mattidragon.jsonpatcher.docs.parse.Tokenizer;
import dev.mattidragon.jsonpatcher.docs.parse.TypeParser;
import dev.mattidragon.jsonpatcher.docs.type.FunctionDocType;
import dev.mattidragon.jsonpatcher.docs.type.NewDocType;
import dev.mattidragon.jsonpatcher.docs.type.ReferenceDocType;
import dev.mattidragon.jsonpatcher.docs.type.UnionDocType;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

public class TypeParseTests {
    @Test
    public void testBasicTypeParsing() {
        Assertions.assertTrue(
                parse("string | number") instanceof UnionDocType(ReferenceDocType(var a), ReferenceDocType(var b))
                && a.equals("string") && b.equals("number")
        );

        Assertions.assertEquals(
                new FunctionDocType(
                        List.of(),
                        List.of(new FunctionDocType.Argument(new ReferenceDocType("string"), Optional.of("s"), FunctionDocType.Argument.Kind.REGULAR),
                                new FunctionDocType.Argument(new ReferenceDocType("string"), Optional.empty(), FunctionDocType.Argument.Kind.REGULAR)),
                        new ReferenceDocType("string")),
                parse("(s: string, string) -> string")
        );
    }

    private NewDocType parse(String code) {
        var tokens = new Tokenizer(code, new SourcePos(new SourceFile("test type", code), 1, 1));
        var diagnostics = new DiagnosticsBuilder();

        var type = TypeParser.parse(tokens, new TreeMetadata(), diagnostics);
        TestUtils.checkDiagnostics(diagnostics.build(), "Failed to parse type", false);

        Assertions.assertFalse(tokens.hasNext(), "All tokens should be consumed");

        return type;
    }
}

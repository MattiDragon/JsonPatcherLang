package dev.mattidragon.jsonpatcher.formatter.test;

import dev.mattidragon.jsonpatcher.formatter.printer.ExpressionPrinter;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrintOptions;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrinter;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;

public class FormatValidator {
    private static final PrettyPrintOptions OPTIONS = PrettyPrintOptions.builder().build();

    private FormatValidator() {}

    public static void validate(Expression expression) {
        var printer = new PrettyPrinter(OPTIONS);
        ExpressionPrinter.prettyPrint(expression, printer);
        var code = printer.getOutput();

        var parsed = TestUtils.parseExpression(code);

        Assertions.assertEquals(expression, parsed, () -> "Expression should be equal after pretty-print cycle.\nPretty-printed code:\n" + code);
    }
}

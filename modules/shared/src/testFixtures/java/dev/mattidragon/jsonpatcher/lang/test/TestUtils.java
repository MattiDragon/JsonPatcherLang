package dev.mattidragon.jsonpatcher.lang.test;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.SimpleLangConfig;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceFile;
import io.github.mattidragon.jsonpatcher.lang.ast.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.parse.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.ContextBuilder;
import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.ValueExpression;
import io.github.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import io.github.mattidragon.jsonpatcher.lang.ast.statement.BlockStatement;
import io.github.mattidragon.jsonpatcher.lang.ast.statement.EmptyStatement;
import io.github.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.function.Consumer;

public class TestUtils {
    public static final SourceFile FILE = new SourceFile("test file", "00");
    public static final SourceSpan POS = new SourceSpan(new SourcePos(FILE, 1, 1), new SourcePos(FILE, 1, 2));
    public static final LangConfig CONFIG = new SimpleLangConfig(false, true);
    public static final Consumer<Value> EMPTY_DEBUG_CONSUMER = (value) -> {};
    public static final Consumer<ContextBuilder> CONTEXT_BUILDER_CONSUMER = builder -> builder.debugConsumer(EMPTY_DEBUG_CONSUMER);

    public static void testCode(Runtime runtime, String code) {
        var result = Parser.parse(CONFIG, Lexer.lex(CONFIG, code, "test file").tokens());
        if (!result.errors().isEmpty()) {
            var error = new RuntimeException("Expected successful parse");
            result.errors().forEach(error::addSuppressed);
            AssertionFailureBuilder.assertionFailure()
                    .message("Expected successful parse")
                    .cause(error)
                    .buildAndThrow();
            return;
        }

        var program = result.program();
        
        Assertions.assertDoesNotThrow(() -> runtime.prepare(program, ).run(CONTEXT_BUILDER_CONSUMER, CONFIG));
    }

    public static void testCode(Runtime runtime, String code, Value expected) {
        var result = Parser.parse(CONFIG, Lexer.lex(CONFIG, code, "test file").tokens());
        if (!result.errors().isEmpty()) {
            var error = new RuntimeException("Expected successful parse");
            result.errors().forEach(error::addSuppressed);
            AssertionFailureBuilder.assertionFailure()
                    .message("Expected successful parse")
                    .cause(error)
                    .buildAndThrow();
            return;
        }

        var program = result.program();

        var output = new Value[1];

        Assertions.assertDoesNotThrow(() -> {
            runtime.prepare(program, ).run(
                    builder -> builder.debugConsumer(EMPTY_DEBUG_CONSUMER)
                            .variable("testResult", new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (ctx, args, pos) -> {
                                output[0] = args.getFirst();
                                return Value.NullValue.NULL;
                            })),
                    CONFIG
            );
        });

        Assertions.assertNotNull(output[0], "testResult should be called");
        assertEquals(expected, output[0]);
    }

    public static void testExpression(Runtime runtime, String code, Value expected) {
        testCode(runtime, "testResult(" + code + ");", expected);
    }

    public static void assertEquals(Value expected, Value actual) {
        if (!areEqual(expected, actual)) {
            AssertionFailureBuilder.assertionFailure().expected(expected).actual(actual).buildAndThrow();
        }
    }

    public static boolean areEqual(Value v1, Value v2) {
        if (v1.equals(v2)) return true;

        if (v1 instanceof Value.ObjectValue o1 && v2 instanceof Value.ObjectValue o2) {
            if (o1.value().size() != o2.value().size()) return false;

            for (var entry : o1.value().entrySet()) {
                if (!o2.value().containsKey(entry.getKey())) return false;
                if (!areEqual(entry.getValue(), o2.value().get(entry.getKey()))) return false;
            }
            return true;
        }

        if (v1 instanceof Value.ArrayValue a1 && v2 instanceof Value.ArrayValue a2) {
            if (a1.value().size() != a2.value().size()) return false;

            for (int i = 0; i < a1.value().size(); i++) {
                if (!areEqual(a1.value().get(i), a2.value().get(i))) return false;
            }
            return true;
        }

        return false;
    }

    public static Expression trueExpression() {
        return new ValueExpression(Value.BooleanValue.of(true));
    }

    public static Expression falseExpression() {
        return new ValueExpression(Value.BooleanValue.of(false));
    }

    public static Expression nullExpression() {
        return new ValueExpression(Value.NullValue.NULL);
    }

    public static Expression stringExpression(String value) {
        return new ValueExpression(new Value.StringValue(value));
    }

    public static Expression numberExpression(double value) {
        return new ValueExpression(new Value.NumberValue(value));
    }

    public static Statement emptyStatement() {
        return new EmptyStatement();
    }

    public static Statement blockStatement(Statement... statements) {
        return new BlockStatement(List.of(statements), POS);
    }
}

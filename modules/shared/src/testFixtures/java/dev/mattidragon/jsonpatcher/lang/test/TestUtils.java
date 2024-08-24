package dev.mattidragon.jsonpatcher.lang.test;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.ast.Program;
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
import io.github.mattidragon.jsonpatcher.lang.runtime.stdlib.LibraryBuilder;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.Assertions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class TestUtils {
    @SuppressWarnings("unused")
    public static final Collection<Runtime> TEST_RUNTIMES = List.of(Runtime.RUNTIMES.get("legacy"));
    public static final SourceFile FILE = new SourceFile("test file", "00");
    public static final SourceSpan POS = new SourceSpan(new SourcePos(FILE, 1, 1), new SourcePos(FILE, 1, 2));
    public static final LangConfig CONFIG = new LangConfig(LangConfig.StackTraceMode.SHORT);
    public static final Consumer<Value> EMPTY_DEBUG_CONSUMER = (value) -> {};
    public static final Consumer<ContextBuilder> CONTEXT_BUILDER_CONSUMER = builder -> builder.debugConsumer(EMPTY_DEBUG_CONSUMER);

    public static void testCode(Runtime runtime, String code) {
        var result = parseFull(code);
        var program = result.program();
        
        Assertions.assertDoesNotThrow(() -> runtime.prepare(program, result.treeMetadata()).run(CONTEXT_BUILDER_CONSUMER, CONFIG));
    }

    public static void testCode(Runtime runtime, String code, Value expected) {
        var result = parseFull(code);
        var output = new Value[1];

        Assertions.assertDoesNotThrow(() -> {
            runtime.prepare(result.program(), result.treeMetadata()).run(
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
        return new BlockStatement(List.of(statements));
    }

    public static Expression parseExpression(String code) {
        var lex = Lexer.lex(CONFIG, code, "test program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        return Parser.parseExpression(CONFIG, lex.tokens());
    }

    public static Program parseProgram(String code) {
        var lex = Lexer.lex(CONFIG, code, "test program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        var parse = Parser.parse(CONFIG, lex.tokens());
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, parse.errors(), "Failed to parse");
        return parse.program();
    }

    public static Parser.Result parseFull(String code) {
        var lex = Lexer.lex(CONFIG, code, "test program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        var parse = Parser.parse(CONFIG, lex.tokens());
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, parse.errors(), "Failed to parse");
        return parse;
    }

    public static LibraryBuilder.FunctionContext createTestFunctionContext() {
        return new LibraryBuilder.FunctionContext(new PatchFunction.BuiltInPatchFunction.Context() {
            @Override
            public LangConfig config() {
                return CONFIG;
            }

            @Override
            public Value execute(PatchFunction function, List<Value> args, SourceSpan callPos) {
                throw new UnsupportedOperationException("execute");
            }

            @Override
            public void log(Value value) {
                throw new UnsupportedOperationException("log");
            }
        }, POS);
    }
}

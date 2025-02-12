package dev.mattidragon.jsonpatcher.lang.test;

import dev.mattidragon.jsonpatcher.lang.analysis.poscheck.PosChecker;
import dev.mattidragon.jsonpatcher.lang.ast.*;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.BlockStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.EmptyStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;
import dev.mattidragon.jsonpatcher.lang.runtime.*;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestUtils {
    public static final SourceFile FILE = new SourceFile("test file", "00");
    public static final SourceSpan POS = new SourceSpan(new SourcePos(FILE, 1, 1), new SourcePos(FILE, 1, 2));
    public static final LangConfig CONFIG = new LangConfig(LangConfig.StackTraceMode.SHORT);
    public static final Consumer<Value> EMPTY_DEBUG_CONSUMER = (value) -> {};
    public static final Consumer<RuntimeContextBuilder> RUNTIME_CONTEXT_BUILDER_CONSUMER = builder -> builder.addStdlib().debugConsumer(EMPTY_DEBUG_CONSUMER);
    public static final Consumer<PreparationContextBuilder> PREPARE_CONTEXT_BUILDER_CONSUMER = PreparationContextBuilder::declareStdlib;

    public static void testCode(Runtime runtime, String code) {
        var result = parseFull(code);
        var program = result.program();

        Assertions.assertDoesNotThrow(() -> runtime.prepare(program, result.treeMetadata(), PREPARE_CONTEXT_BUILDER_CONSUMER).run(RUNTIME_CONTEXT_BUILDER_CONSUMER, CONFIG));
    }
    
    public static void testCode(TestRunner runtime, String code) {
        var result = parseFull(code);
        var program = result.program();
        
        Assertions.assertDoesNotThrow(() -> runtime.executeCode(program, result.treeMetadata(), Map.of()), "Failed to run test code");
    }
    
    public static void testCode(TestRunner runtime, String code, Map<String, Value.ObjectValue> libs) {
        var result = parseFull(code);
        var program = result.program();
        
        Assertions.assertDoesNotThrow(() -> runtime.executeCode(program, result.treeMetadata(), libs), "Failed to run test code");
    }

    
    public static void runCode(TestRunner runtime, String code) {
        var result = parseFull(code);
        var program = result.program();
        
        runtime.executeCode(program, result.treeMetadata(), Map.of());
    }
    
    public static void runCode(TestRunner runtime, String code, Map<String, Value.ObjectValue> libs) {
        var result = parseFull(code);
        var program = result.program();
        
        runtime.executeCode(program, result.treeMetadata(), libs);
    }

    public static void testCode(Runtime runtime, String code, Value expected) {
        var result = parseFull(code);
        var output = new Value[1];

        Assertions.assertDoesNotThrow(() -> {
            runtime.prepare(result.program(), result.treeMetadata(), builder -> builder.declareVariable("testResult")).run(
                    builder -> builder.debugConsumer(EMPTY_DEBUG_CONSUMER)
                            .fillVariable("testResult", new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (ctx, args) -> {
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

        if (v1 instanceof Value.ObjectValue(var o1, var frozen1) && v2 instanceof Value.ObjectValue(var o2, var frozen2)) {
            if (frozen1 != frozen2) return false;
            if (o1.size() != o2.size()) return false;

            for (var entry : o1.entrySet()) {
                if (!o2.containsKey(entry.getKey())) return false;
                if (!areEqual(entry.getValue(), o2.get(entry.getKey()))) return false;
            }
            return true;
        }

        if (v1 instanceof Value.ArrayValue(var a1, var frozen1) && v2 instanceof Value.ArrayValue(var a2, var frozen2)) {
            if (frozen1 != frozen2) return false;
            if (a1.size() != a2.size()) return false;

            for (int i = 0; i < a1.size(); i++) {
                if (!areEqual(a1.get(i), a2.get(i))) return false;
            }
            return true;
        }

        return false;
    }

    public static Expression trueExpression() {
        return new BooleanExpression(true);
    }

    public static Expression falseExpression() {
        return new BooleanExpression(false);
    }

    public static Expression nullExpression() {
        return new NullExpression();
    }

    public static Expression stringExpression(String value) {
        return new StringExpression(value);
    }

    public static Expression numberExpression(double value) {
        return new NumberExpression(value);
    }

    public static Statement emptyStatement() {
        return new EmptyStatement();
    }

    public static Statement blockStatement(Statement... statements) {
        return new BlockStatement(List.of(statements));
    }

    public static Expression parseExpression(String code) {
        var diagnosticBuilder = new DiagnosticsBuilder();

        var lex = Lexer.lex(code, "test program", diagnosticBuilder);
        var expression = Parser.parseExpression(lex.tokens(), diagnosticBuilder);
        checkDiagnostics(diagnosticBuilder.build(), "Parsing errors", false);

        return expression;
    }

    public static Program parseProgram(String code) {
        return parseFull(code).program();
    }

    public static Parser.Result parseFull(String code) {
        var diagnosticBuilder = new DiagnosticsBuilder();

        var lex = Lexer.lex(code, "test program", diagnosticBuilder);
        var parse = Parser.parse(lex.tokens(), diagnosticBuilder);

        checkDiagnostics(diagnosticBuilder.build(), "Parsing errors", false);

        checkPos(parse.program(), parse.treeMetadata());
        return parse;
    }

    private static void checkPos(ProgramNode program, TreeMetadata treeMetadata) {
        var diagnosticBuilder = new DiagnosticsBuilder();
        PosChecker.analyse(program, treeMetadata, diagnosticBuilder);
        checkDiagnostics(diagnosticBuilder.build(), "Inconsistent positions", false);
    }

    public static void checkDiagnostics(Diagnostics diagnostics, String errorMsg, boolean allowWarnings) {
        var illegal = allowWarnings ? diagnostics.errors() : diagnostics.get(Diagnostic.Kind.ERROR, Diagnostic.Kind.INTERNAL_ERROR, Diagnostic.Kind.WARNING);
        if (!illegal.iterator().hasNext()) return;

        var wholeString = new StringBuilder("\n");
        for (var diagnostic : illegal) {
            wholeString.append(diagnostic.toDisplay()).append('\n');
        }
        AssertionFailureBuilder.assertionFailure()
                .message(errorMsg)
                .reason(wholeString.toString())
                .buildAndThrow();
    }

    private static <T extends Throwable> T combineErrors(Iterable<T> errors) {
        var iter = errors.iterator();
        if (!iter.hasNext()) throw new IllegalStateException("No errors provided");

        T first = iter.next();
        while (iter.hasNext()) {
            first.addSuppressed(iter.next());
        }
        return first;
    }

    public static PlatformContext createTestFunctionContext() {
        return new PlatformContext() {
            @Override
            public RuntimeException createException(String message) {
                return new RuntimeException("Error in test: " + message);
            }

            @Override
            public RuntimeException createException(String message, Exception cause) {
                return new RuntimeException("Error in test: " + message, cause);
            }

            @Override
            public LangConfig config() {
                return CONFIG;
            }

            @Override
            public Value execute(PatchFunction function, List<Value> args) {
                throw new UnsupportedOperationException("execute");
            }

            @Override
            public void log(Value value) {
                throw new UnsupportedOperationException("log");
            }
        };
    }
}

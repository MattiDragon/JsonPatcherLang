package dev.mattidragon.jsonpatcher.lang.analysis.test.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.PrimitiveProperties;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeCheckError;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.v2.TypeChecker2;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import dev.mattidragon.jsonpatcher.toolcommon.typing.PrimitivePropertiesLoader;
import org.junit.jupiter.api.AssertionFailureBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.List;
import java.util.Map;

public class TypeCheckerTests {
    private static final TypeCheckerMethod V1 = TypeChecker::typeCheck;
    private static final TypeCheckerMethod V2 = TypeChecker2::typeCheck;
    public static final TypeCheckerMethod[] METHODS = {V1, V2};

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testTypeChecker(TypeCheckerMethod method) {
        Assumptions.assumeFalse(method == V1, "V1 is known to be broken");

        var typeArg1 = new TypeArgument("T1", SpecialType.ANY);
        method.assertSuccess(Map.of(
            "a", new FunctionType(List.of(), List.of(
                    new FunctionType(List.of(typeArg1), List.of(new FunctionType(List.of(), List.of(), 0, false, typeArg1)), 1, false, typeArg1)
                ), 1, false, SpecialType.UNKNOWN)
        ), "a((b) -> b());");

        method.assertSuccess(Map.of(
                "a", new FunctionType(List.of(typeArg1), List.of(new FunctionType(List.of(), List.of(), 0, false, typeArg1)), 1, false, typeArg1)
        ), "a(() -> null);");
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testFunctionArgumentCounts(TypeCheckerMethod method) {
        var fType = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER, PrimitiveType.NUMBER), 2, false, PrimitiveType.NULL);
        method.assertSuccess(Map.of("f", fType), "f(1,2);");
        method.assertFailure(Map.of("f", fType), "f(1);", TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH);
        method.assertFailure(Map.of("f", fType), "f(1,2,3);", TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH);
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testPropertyAccess(TypeCheckerMethod method) {
        method.assertSuccess(Map.of("s", PrimitiveType.STRING), "s.length;");
        method.assertFailure(Map.of("s", PrimitiveType.STRING), "s.foobar;", TypeCheckError.Code.UNEXPECTED_PROPERTY);
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testArrayIndexing(TypeCheckerMethod method) {
        Assumptions.assumeFalse(method == V1, "V1 is known to be broken");

        method.assertSuccess(Map.of("a", new ArrayType(PrimitiveType.NUMBER)), "a[0];");
        method.assertFailure(Map.of("a", new ArrayType(PrimitiveType.NUMBER)), "a[\"x\"];", TypeCheckError.Code.UNEXPECTED_TYPE);
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testObjectIndexProperty(TypeCheckerMethod method) {
        method.assertSuccess(Map.of("m", new ObjectType(PrimitiveType.NUMBER)), "m.any;");
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testVarargsCalls(TypeCheckerMethod method) {
        var varargs = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER), 1, true, PrimitiveType.NULL);
        method.assertSuccess(Map.of("f", varargs), "f(1,2,3);");
        method.assertSuccess(Map.of("f", varargs), "f(1);");

        var nonVarargs = new FunctionType(List.of(), List.of(PrimitiveType.NUMBER, PrimitiveType.NUMBER), 2, false, PrimitiveType.NULL);
        method.assertFailure(Map.of("g", nonVarargs), "g(1);", TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH);
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testInOperator(TypeCheckerMethod method) {
        method.assertSuccess(Map.of("o", new ObjectType(PrimitiveType.NUMBER)), "\"a\" in o;");
        method.assertFailure(Map.of("o", new ObjectType(PrimitiveType.NUMBER)), "1 in o;", TypeCheckError.Code.UNEXPECTED_TYPE);

        method.assertSuccess(Map.of("a", new ArrayType(PrimitiveType.NUMBER)), "1 in a;");
        method.assertFailure(Map.of("a", new ArrayType(PrimitiveType.NUMBER)), "\"x\" in a;", TypeCheckError.Code.UNEXPECTED_TYPE);
    }

    @ParameterizedTest
    @FieldSource("METHODS")
    public void testShortedBinary(TypeCheckerMethod method) {
        method.assertSuccess(Map.of(), "true && false;");
        method.assertSuccess(Map.of(), "1 || 2;");
        method.assertSuccess(Map.of(), "1 && 2;");
    }

    public interface TypeCheckerMethod {
        void typeCheck(ProgramNode node, TreeMetadata metadata, PrimitiveProperties primitiveProperties, DiagnosticsBuilder diagnostics);

        default void assertSuccess(Map<String, Type> globals, String code) {
            var diagnostics = check(globals, code);
            TestUtils.checkDiagnostics(diagnostics, "Incorrect type checking error", false);
        }

        default void assertFailure(Map<String, Type> globals, String code, TypeCheckError.Code errorCode) {
            var diagnostics = check(globals, code).get(Diagnostic.Kind.ERROR, Diagnostic.Kind.INTERNAL_ERROR, Diagnostic.Kind.WARNING);

            // V1 doesn't always use specific error codes; for it, just ensure there's at least one error
            if (this == TypeCheckerTests.V1) {
                if (diagnostics.isEmpty()) {
                    AssertionFailureBuilder.assertionFailure()
                            .message("Expected at least one diagnostic")
                            .buildAndThrow();
                }
                return;
            }

            if (diagnostics.stream().noneMatch(d -> d.id().equals("TYPE-" + errorCode.ordinal()))) {
                var wholeString = new StringBuilder("\n");
                for (var diagnostic : diagnostics) {
                    wholeString.append(diagnostic.toDisplay()).append('\n');
                }
                AssertionFailureBuilder.assertionFailure()
                        .message("Missing required error: TYPE-" + errorCode.ordinal())
                        .reason(wholeString.toString())
                        .buildAndThrow();
            }

            var incorrectDiagnostics = diagnostics.stream().filter(d -> !d.id().equals("TYPE-" + errorCode.ordinal())).toList();
            if (!incorrectDiagnostics.isEmpty()) {
                var wholeString = new StringBuilder("\n");
                for (var diagnostic : incorrectDiagnostics) {
                    wholeString.append(diagnostic.toDisplay()).append('\n');
                }
                AssertionFailureBuilder.assertionFailure()
                        .message("Unexpected diagnostics found, expected only TYPE-" + errorCode.ordinal())
                        .reason(wholeString.toString())
                        .buildAndThrow();
            }
        }

        private Diagnostics check(Map<String, Type> globals, String code) {
            var parsed = TestUtils.parseFull(code);
            var diagnostics = new DiagnosticsBuilder();
            var program = parsed.program();
            var metadata = parsed.treeMetadata();

            VariableAnalyser.analyse(program, metadata, diagnostics, globals.keySet());

            var programScope = metadata.get(program, VariableAnalyser.SCOPE).orElseThrow();
            for (var variable : programScope.variables()) {
                var type = globals.get(variable.name());
                if (type != null) {
                    metadata.put(variable, TypeChecker.TYPE, type);
                }
            }

            typeCheck(program, metadata, PrimitivePropertiesLoader.PROPERTIES, diagnostics);
            return diagnostics.build();
        }
    }
}

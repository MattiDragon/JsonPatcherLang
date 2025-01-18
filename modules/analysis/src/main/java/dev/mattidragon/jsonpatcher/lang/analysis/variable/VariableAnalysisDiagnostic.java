package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

record VariableAnalysisDiagnostic(
        Kind kind,
        String id,
        String message,
        @Nullable SourceSpan pos,
        ProgramNode node
) implements Diagnostic {
    public static Diagnostic unnecessaryMutability(String variable, @Nullable SourceSpan pos, ProgramNode node) {
        return new VariableAnalysisDiagnostic(
                Kind.WARNING,
                VariableAnalysisDiagnostics.UNNECESSARY_MUTABLE,
                "Variable '%s' can be made immutable".formatted(variable),
                pos,
                node
        );
    }

    public static Diagnostic unusedVariable(String variable, @Nullable SourceSpan pos, ProgramNode node) {
        return new VariableAnalysisDiagnostic(
                Kind.UNUSED,
                VariableAnalysisDiagnostics.UNUSED_VARIABLE,
                "Variable '%s' is never used".formatted(variable),
                pos,
                node
        );
    }

    public static Diagnostic missingVariable(String variable, @Nullable SourceSpan pos, ProgramNode node) {
        return new VariableAnalysisDiagnostic(
                Kind.ERROR,
                VariableAnalysisDiagnostics.UNKNOWN_VARIABLE,
                "Cannot find variable '%s'".formatted(variable),
                pos,
                node
        );
    }

    public static Diagnostic duplicateVariable(String variable, @Nullable SourceSpan pos, ProgramNode node) {
        return new VariableAnalysisDiagnostic(
                Kind.ERROR,
                VariableAnalysisDiagnostics.DUPLICATE_VARIABLE,
                "Variable '%s' would illegally shadow another variable by the same name".formatted(variable),
                pos,
                node
        );
    }

    public static Diagnostic illegalMutation(String variable, @Nullable SourceSpan pos, ProgramNode node) {
        return new VariableAnalysisDiagnostic(
                Kind.ERROR,
                VariableAnalysisDiagnostics.ILLEGAL_MUTATION,
                "Cannot modify '%s' at it is immutable".formatted(variable),
                pos,
                node
        );
    }
}

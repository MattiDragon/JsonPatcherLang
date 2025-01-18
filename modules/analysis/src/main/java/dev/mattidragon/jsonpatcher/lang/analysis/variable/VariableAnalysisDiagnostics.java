package dev.mattidragon.jsonpatcher.lang.analysis.variable;

/**
 * Contains IDs for diagnostics emitted by the variable analyser
 */
public class VariableAnalysisDiagnostics {
    private static final String BASE = "VAR-";
    public static final String UNKNOWN_VARIABLE = BASE + 0;
    public static final String DUPLICATE_VARIABLE = BASE + 1;
    public static final String ILLEGAL_MUTATION = BASE + 2;
    public static final String UNUSED_VARIABLE = BASE + 3;
    public static final String UNNECESSARY_MUTABLE = BASE + 4;

    private VariableAnalysisDiagnostics() {}
}

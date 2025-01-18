package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

/**
 * Contains IDs for diagnostics emitted by the variable analyser
 */
public class PosCheckDiagnostics {
    private static final String BASE = "PC-";
    public static final String ILLEGAL_POS_NESTING = BASE + 0;
    public static final String ILLEGAL_SOURCE_SPAN = BASE + 1;
    public static final String MISSING_METADATA = BASE + 2;

    private PosCheckDiagnostics() {}
}

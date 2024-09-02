package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import java.util.List;

/**
 * A completed variable analysis.
 * @param errors A list of errors that might have occurred
 * @param scopes A list of all scopes found during the analysis
 * @see VariableAnalyser
 */
public record VariableAnalysis(List<VariableAnalyser.AnalysisError> errors, List<Scope> scopes) {
}

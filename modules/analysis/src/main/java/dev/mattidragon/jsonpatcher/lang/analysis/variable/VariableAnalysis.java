package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import java.util.List;

/**
 * A completed variable analysis.
 * @param scopes A list of all scopes found during the analysis
 * @see VariableAnalyser
 */
public record VariableAnalysis(List<Scope> scopes) {
}

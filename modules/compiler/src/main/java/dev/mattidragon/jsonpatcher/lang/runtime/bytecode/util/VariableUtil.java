package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;

public class VariableUtil {
    private VariableUtil() {
    }

    public static boolean needsBoxing(Variable variable, TreeMetadata metadata) {
        if (variable.definition() instanceof FunctionDeclarationStatement statement) {
            // Special case: If a function declaration statement captures its own variable
            // the variable must be boxed, despite never being mutated.
            var scope = metadata.get(statement.value(), VariableAnalyser.SCOPE).orElse(null);
            if (scope instanceof FunctionScope functionScope && functionScope.captures().contains(variable)) {
                return true;
            }
        }
        return variable.isCaptured() && variable.isMutated();
    }
}

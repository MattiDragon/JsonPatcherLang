package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

public class VariableUtil {
    private VariableUtil() {
    }

    public static boolean needsBoxing(Variable variable, TreeMetadata metadata) {
        if (variable.isCapturedEarly()) {
            return true;
        }
        return variable.isCaptured() && variable.isMutated();
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import org.jspecify.annotations.Nullable;

sealed abstract class MutableScope implements Scope permits ApplyScope, BlockScope, FunctionScope, ProgramScope {
    @Nullable
    VariableRef find(String name) {
        for (var variable : variables()) {
            if (variable.name().equals(name)) return variable;
        }
        return switch (parent()) {
            case MutableScope scope -> scope.find(name);
            case null -> null;
        };
    }

    public boolean has(String name) {
        for (var variable : variables()) {
            if (variable.name().equals(name)) return true;
        }
        return switch (parent()) {
            case MutableScope scope -> scope.has(name);
            case null -> false;
        };
    }

    public void define(Variable variable) {
        variables().add(variable);
    }
}

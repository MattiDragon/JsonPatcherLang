package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

public interface PreparationContextBuilder {
    default PreparationContextBuilder declareVariables(Iterable<String> names) {
        for (var name : names) {
            declareVariable(name);
        }
        return this;
    }

    default PreparationContextBuilder declareVariables(String... names) {
        for (var name : names) {
            declareVariable(name);
        }
        return this;
    }

    PreparationContextBuilder declareVariable(String name);
    
    default PreparationContextBuilder declareStdlib() {
        return declareVariables(Libraries.BUILTIN.keySet());
    }
}

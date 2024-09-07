package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class VariableHolder {
    private final Set<String> declared = new HashSet<>();
    
    public void prepare(Consumer<PreparationContextBuilder> preparationContextConsumer) {
        preparationContextConsumer.accept(new PreparationContextBuilder() {
            @Override
            public PreparationContextBuilder declareVariable(String name) {
                declared.add(name);
                return this;
            }
        });
    }

    public Set<String> getDeclared() {
        return declared;
    }
}

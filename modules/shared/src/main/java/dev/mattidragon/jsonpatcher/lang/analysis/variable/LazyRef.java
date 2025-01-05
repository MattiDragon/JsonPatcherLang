package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class LazyRef implements VariableRef {
    private final String name;
    private final MutableScope scope;
    private final List<Consumer<Variable>> resolutionCallbacks;
    private @Nullable Variable value;

    LazyRef(String name, MutableScope scope) {
        this.name = name;
        this.scope = scope;
        this.resolutionCallbacks = new ArrayList<>();
    }

    public void resolve() {
        if (value != null) {
            return;
        }
        if (scope.find(name) instanceof Variable variable) {
            this.value = variable;
            for (var callback : resolutionCallbacks) {
                callback.accept(value);
            }
        }
    }
    
    void onResolve(Consumer<Variable> callback) {
        resolutionCallbacks.add(callback);
    }

    public @Nullable Variable getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
}

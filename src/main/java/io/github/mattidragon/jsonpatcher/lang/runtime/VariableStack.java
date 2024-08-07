package io.github.mattidragon.jsonpatcher.lang.runtime;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.HashMap;

public final class VariableStack {
    private final LangConfig config;
    private final @Nullable VariableStack parent;
    private final HashMap<String, Value> mutable = new HashMap<>();
    private final HashMap<String, Value> immutable = new HashMap<>();

    public VariableStack(LangConfig config) {
        this.config = config;
        this.parent = null;
    }

    public VariableStack(LangConfig config, @Nullable VariableStack parent) {
        this.config = config;
        this.parent = parent;
    }

    public Value getVariable(String name, SourceSpan pos) {
        if (mutable.containsKey(name)) {
            return mutable.get(name);
        }
        if (immutable.containsKey(name)) {
            return immutable.get(name);
        }
        if (parent != null) {
            return parent.getVariable(name, pos);
        }
        throw new EvaluationException(config, "Cannot find variable with name %s".formatted(name), pos);
    }

    @VisibleForTesting
    public boolean hasVariable(String name) {
        if (mutable.containsKey(name)) return true;
        if (immutable.containsKey(name)) return true;
        if (parent != null) return parent.hasVariable(name);
        return false;
    }

    public void setVariable(String name, Value value, SourceSpan pos) {
        if (parent != null && parent.hasVariable(name)) {
            parent.setVariable(name, value, pos);
        } else if (mutable.containsKey(name)) {
            mutable.put(name, value);
        } else if (immutable.containsKey(name)) {
            throw new EvaluationException(config, "Attempt to assign to mutable variable %s".formatted(name), pos);
        } else {
            throw new EvaluationException(config, "Cannot find variable with name %s".formatted(name), pos);
        }
    }

    public void createVariable(String name, Value value, boolean mutable, SourceSpan pos) {
        if (hasVariable(name)) throw new EvaluationException(config, "Cannot create variable with duplicate name: %s".formatted(name), pos);
        if (mutable) this.mutable.put(name, value);
        else this.immutable.put(name, value);
    }

    /**
     * Creates a variable without checking for duplicates.
     */
    public void createVariableUnsafe(String name, Value value, boolean mutable) {
        if (mutable) this.mutable.put(name, value);
        else this.immutable.put(name, value);
    }

    public void deleteVariable(String name, SourceSpan pos) {
        if (immutable.containsKey(name)) {
            immutable.remove(name);
            return;
        }
        if (mutable.containsKey(name)) {
            mutable.remove(name);
            return;
        }
        if (hasVariable(name)) throw new EvaluationException(config, "Cannot delete variable from outer scope: %s".formatted(name), pos);
        throw new EvaluationException(config, "Cannot find variable with name %s".formatted(name), pos);
    }
}

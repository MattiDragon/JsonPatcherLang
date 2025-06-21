package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;

public final class FunctionScope implements MutableScope {
    private final FunctionExpression function;
    private final RootVariable root = new RootVariable();
    private final List<Variable> earlyAccessVariables;
    private final List<Variable> variables;
    private final Set<Variable> captures;
    private final MutableScope parent;

    FunctionScope(FunctionExpression function, MutableScope parent, List<Variable> earlyAccessVariables) {
        this.function = function;
        this.variables = new ArrayList<>();
        this.captures = new HashSet<>();
        this.earlyAccessVariables = earlyAccessVariables;
        this.parent = parent;
    }

    @Override
    public @Nullable Variable find(String name) {
        for (var variable : earlyAccessVariables) {
            if (variable.name().equals(name)) {
                if (!variables.contains(variable)) {
                    variable.markCapturedEarly();
                    addCapture(variable);
                }
                return variable;
            }
        }

        var variable = MutableScope.super.find(name);
        if (variable != null && !variables.contains(variable)) {
            addCapture(variable);
        }
        return variable;
    }

    private void addCapture(Variable variable) {
        captures.add(variable);
        variable.markCaptured();
    }

    @Override
    public RootVariable root() {
        return root;
    }

    public FunctionExpression function() {
        return function;
    }

    @Override
    public List<Variable> variables() {
        return variables;
    }
    
    public Collection<Variable> captures() {
        return captures;
    }

    @Override
    @NonNull
    public Scope parent() {
        return parent;
    }
}

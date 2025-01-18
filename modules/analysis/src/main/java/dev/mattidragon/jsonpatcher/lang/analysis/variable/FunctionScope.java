package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import org.jspecify.annotations.NonNull;

import java.util.*;

public final class FunctionScope extends MutableScope {
    private final FunctionExpression function;
    private final RootVariable root = new RootVariable();
    private final List<Variable> variables;
    private final Set<Variable> captures;
    private final MutableScope parent;

    FunctionScope(FunctionExpression function, MutableScope parent) {
        this.function = function;
        this.variables = new ArrayList<>();
        this.captures = new HashSet<>();
        this.parent = parent;
    }

    @Override
    VariableRef find(String name) {
        for (var variable : variables()) {
            if (variable.name().equals(name)) {
                return variable;
            }
        }

        var ref = parent.find(name);
        if (!(ref instanceof Variable)) {
            ref = new LazyRef(name, parent);
        }
        switch (ref) {
            case LazyRef lazyRef -> lazyRef.onResolve(this::addCapture);
            case Variable variable -> addCapture(variable);
        }

        return ref;
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
    public MutableScope parent() {
        return parent;
    }
}

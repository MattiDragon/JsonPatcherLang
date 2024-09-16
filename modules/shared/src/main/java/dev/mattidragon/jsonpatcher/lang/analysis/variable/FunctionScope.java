package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class FunctionScope extends MutableScope {
    private final FunctionExpression function;
    private final RootVariable root = new RootVariable();
    private final List<Variable> variables;
    private final List<Variable> captures;
    private final MutableScope parent;

    FunctionScope(FunctionExpression function, MutableScope parent) {
        this.function = function;
        this.variables = new ArrayList<>();
        this.captures = new ArrayList<>();
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
        if (ref == null) {
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
    
    public List<Variable> captures() {
        return captures;
    }

    @Override
    @NotNull
    public MutableScope parent() {
        return parent;
    }
}

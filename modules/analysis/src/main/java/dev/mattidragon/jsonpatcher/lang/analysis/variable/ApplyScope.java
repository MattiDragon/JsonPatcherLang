package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.statement.ApplyStatement;

import java.util.List;

/**
 * The scope of an apply statement. 
 * Apply statements have a unique situation where they override the root value and don't introduce a new variable scope.
 */
public final class ApplyScope implements MutableScope {
    private final ApplyStatement statement;
    private final RootVariable root;
    private final MutableScope parent;

    ApplyScope(ApplyStatement statement, MutableScope parent) {
        this.statement = statement;
        this.root = new RootVariable();
        this.parent = parent;
    }

    @Override
    public List<Variable> variables() {
        return List.of();
    }

    @Override
    public void define(Variable variable) {
        parent.define(variable);
    }

    public ApplyStatement statement() {
        return statement;
    }

    @Override
    public RootVariable root() {
        return root;
    }

    @Override
    public Scope parent() {
        return parent;
    }
}

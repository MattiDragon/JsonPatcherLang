package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple lexical scope that contains its own variables
 */
public final class BlockScope extends MutableScope {
    private final Statement block;
    private final List<Variable> variables;
    private final MutableScope parent;

    BlockScope(Statement block, MutableScope parent) {
        this.block = block;
        this.variables = new ArrayList<>();
        this.parent = parent;
    }

    public Statement block() {
        return block;
    }

    @Override
    public List<Variable> variables() {
        return variables;
    }

    @Override
    public Scope parent() {
        return parent;
    }
}

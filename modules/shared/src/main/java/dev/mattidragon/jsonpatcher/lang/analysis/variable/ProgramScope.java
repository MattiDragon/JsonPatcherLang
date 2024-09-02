package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ProgramScope extends MutableScope {
    private final Program program;
    private final RootVariable root;
    private final List<Variable> variables;

    ProgramScope(Program program) {
        this.program = program;
        this.root = new RootVariable();
        this.variables = new ArrayList<>();
    }

    @Override
    public @Nullable Scope parent() {
        return null;
    }

    public Program program() {
        return program;
    }

    @Override
    public RootVariable root() {
        return root;
    }

    @Override
    public List<Variable> variables() {
        return variables;
    }
}

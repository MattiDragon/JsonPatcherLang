package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Variable implements VariableRef {
    private final String name;
    private final boolean mutable;
    private final ProgramNode definition;
    private final List<ProgramNode> usages = new ArrayList<>();
    private boolean captured = false;

    Variable(String name, boolean mutable, ProgramNode definition) {
        this.name = name;
        this.mutable = mutable;
        this.definition = definition;
    }

    public String name() {
        return name;
    }

    public boolean mutable() {
        return mutable;
    }
    
    public boolean isCaptured() {
        return captured;
    }

    public ProgramNode definition() {
        return definition;
    }

    public List<ProgramNode> usages() {
        return Collections.unmodifiableList(usages);
    }

    void addUsage(ProgramNode node) {
        usages.add(node);
    }
    
    void markCaptured() {
        captured = true;
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Variable implements MetadataHolder {
    private final String name;
    private final boolean mutable;
    private final ProgramNode definition;
    private final List<ProgramNode> usages = new ArrayList<>();
    private boolean captured = false;
    private boolean mutated = false;
    // This flag is set when a variable is captured by functions its own initializer
    // This allows recursion without mutable variables
    private boolean capturedEarly = false;

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

    public boolean isMutated() {
        return mutated;
    }

    public boolean isCapturedEarly() {
        return capturedEarly;
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

    void markMutated() {
        mutated = true;
    }

    void markCapturedEarly() {
        capturedEarly = true;
    }

    public boolean stdlib() {
        return definition instanceof Program;
    }

    @Override
    public Iterable<MetadataHolder> getChildren() {
        return List.of();
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.variable;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public sealed interface Scope permits MutableScope {
    List<Variable> variables();
    
    @Nullable Scope parent();
    
    default RootVariable root() {
        return Objects.requireNonNull(parent(), "parent must be present or root() must be overridden").root();
    }
    
    default void define(Variable variable) {
        variables().add(variable);
    }
}

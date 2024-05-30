package io.github.mattidragon.jsonpatcher.lang.runtime;

public interface ProgramNode {
    Iterable<? extends ProgramNode> getChildren();
}

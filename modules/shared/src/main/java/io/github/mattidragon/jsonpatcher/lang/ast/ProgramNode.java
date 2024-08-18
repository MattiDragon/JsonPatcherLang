package io.github.mattidragon.jsonpatcher.lang.ast;

public interface ProgramNode {
    Iterable<? extends ProgramNode> getChildren();
}

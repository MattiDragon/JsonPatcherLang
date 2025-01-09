package dev.mattidragon.jsonpatcher.lang.ast;

public interface ProgramNode {
    Iterable<? extends ProgramNode> getChildren();
}

package dev.mattidragon.jsonpatcher.lang.ast;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

public interface ProgramNode extends MetadataHolder {
    Iterable<? extends ProgramNode> getChildren();
}

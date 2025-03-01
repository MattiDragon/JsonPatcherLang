package dev.mattidragon.jsonpatcher.lang.ast.meta;

public interface MetadataHolder {
    Iterable<? extends MetadataHolder> getChildren();
}

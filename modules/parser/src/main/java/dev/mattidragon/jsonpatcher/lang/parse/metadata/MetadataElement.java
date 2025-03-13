package dev.mattidragon.jsonpatcher.lang.parse.metadata;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;

public sealed interface MetadataElement extends MetadataHolder
        permits MetadataArray, MetadataBoolean, MetadataNull, MetadataNumber, MetadataObject, MetadataString {
    @Override
    default Iterable<? extends MetadataElement> getChildren() {
        return List.of();
    }
}

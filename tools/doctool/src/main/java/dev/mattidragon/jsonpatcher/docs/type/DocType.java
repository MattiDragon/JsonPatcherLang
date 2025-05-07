package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

public sealed interface DocType extends MetadataHolder
        permits ArrayDocType, ErrorDocType, FunctionDocType, MapDocType, ReferenceDocType, TypeArgumentDocType, UnionDocType {
}

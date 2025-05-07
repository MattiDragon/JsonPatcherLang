package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

public sealed interface NewDocType extends MetadataHolder
        permits ArrayDocType, ErrorDocType, FunctionDocType, MapDocType, ReferenceDocType, TypeArgumentDocType, UnionDocType {
}

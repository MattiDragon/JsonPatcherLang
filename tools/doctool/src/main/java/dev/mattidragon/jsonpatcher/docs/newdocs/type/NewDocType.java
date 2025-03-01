package dev.mattidragon.jsonpatcher.docs.newdocs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

public sealed interface NewDocType extends MetadataHolder
        permits ArrayDocType, ErrorDocType, FunctionDocType, ReferenceDocType, TypeArgumentDocType, UnionDocType {
}

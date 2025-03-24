package dev.mattidragon.jsonpatcher.docs.newdocs;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;

import java.util.List;

public class DocMetadataKeys {
    public static final MetadataKey<List<SourceSpan>> NAMESPACE_POSITIONS = new MetadataKey<>("DocMetadataKeys/NAMESPACE_POSITIONS");

    private DocMetadataKeys() {}
}

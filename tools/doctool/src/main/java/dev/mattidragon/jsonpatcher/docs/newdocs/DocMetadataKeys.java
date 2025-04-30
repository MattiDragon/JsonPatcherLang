package dev.mattidragon.jsonpatcher.docs.newdocs;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;

import java.util.List;

public class DocMetadataKeys {
    public static final MetadataKey<List<SourceSpan>> NAMESPACE_POSITIONS = new MetadataKey<>("DocMetadataKeys/NAMESPACE_POSITIONS");
    public static final MetadataKey<SourceSpan> PROPERTY_OWNER_POS = new MetadataKey<>("DocMetadataKeys/PROPERTY_OWNER_POS");
    public static final MetadataKey<SourceSpan> BASE_TYPE_POS = new MetadataKey<>("DocMetadataKeys/BASE_TYPE_POS");

    private DocMetadataKeys() {}
}

package io.github.mattidragon.jsonpatcher.lang.ast.meta;

import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.List;

public class MetadataKey<T> {
    public static final MetadataKey<SourceSpan> IMPORT_LOCATION_POS = new MetadataKey<>();
    public static final MetadataKey<SourceSpan> IS_TYPE_POS = new MetadataKey<>();

    /**
     * The position of the main keyword or other significant defining token.
     */
    public static final MetadataKey<SourceSpan> KEYWORD_POS = new MetadataKey<>();
    public static final MetadataKey<SourceSpan> SECONDARY_KEYWORD_POS = new MetadataKey<>();
    /**
     * The position of a variable name or other similar name.
     */
    public static final MetadataKey<SourceSpan> NAME_POS = new MetadataKey<>();
    /**
     * The position covering the whole node including all children.
     * Should be present on most if not all nodes after normal parsing.
     */
    public static final MetadataKey<SourceSpan> FULL_POS = new MetadataKey<>();
    /**
     * The main position of the node. 
     * Should be present on almost all nodes and should be preferred over {@link #FULL_POS} for errors.
     */
    public static final MetadataKey<SourceSpan> MAIN_POS = new MetadataKey<>(KEYWORD_POS, NAME_POS, FULL_POS);

    private final List<MetadataKey<T>> parents;

    @SafeVarargs
    public MetadataKey(MetadataKey<T>... parents) {
        this.parents = List.of(parents);
    }

    public List<MetadataKey<T>> getParents() {
        return parents;
    }
}

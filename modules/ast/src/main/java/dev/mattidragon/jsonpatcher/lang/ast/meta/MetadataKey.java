package dev.mattidragon.jsonpatcher.lang.ast.meta;

import dev.mattidragon.jsonpatcher.lang.ast.NumberStyle;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.ObjectInitializerExpression;

import java.util.List;

public class MetadataKey<T> {
    public static final MetadataKey<SourceSpan> IMPORT_LOCATION_POS = new MetadataKey<>("IMPORT_LOCATION_POS");
    public static final MetadataKey<SourceSpan> IS_TYPE_POS = new MetadataKey<>("IS_TYPE_POS");

    /**
     * The position of the main keyword or other significant defining token.
     */
    public static final MetadataKey<SourceSpan> KEYWORD_POS = new MetadataKey<>("KEYWORD_POS");
    public static final MetadataKey<SourceSpan> SECONDARY_KEYWORD_POS = new MetadataKey<>("SECONDARY_KEYWORD_POS");
    /**
     * The position of a variable name or other similar name.
     */
    public static final MetadataKey<SourceSpan> NAME_POS = new MetadataKey<>("NAME_POS");
    /**
     * The position covering the whole node including all children.
     * Should be present on most if not all nodes after normal parsing.
     */
    public static final MetadataKey<SourceSpan> FULL_POS = new MetadataKey<>("FULL_POS");
    /**
     * The main position of the node. 
     * Should be present on almost all nodes and should be preferred over {@link #FULL_POS} for errors.
     */
    public static final MetadataKey<SourceSpan> MAIN_POS = new MetadataKey<>("MAIN_POS", KEYWORD_POS, NAME_POS, FULL_POS);

    public static final MetadataKey<List<SourceSpan>> MULTI_POS = new MetadataKey<>("MULTI_POS");

    public static final MetadataKey<NumberStyle> NUMBER_STYLE = new MetadataKey<>("NUMBER_STYLE");
    public static final MetadataKey<ObjectInitializerExpression.KeyStyle> OBJECT_KEY_STYLE = new MetadataKey<>("OBJECT_KEY_STYLE");

    private final String name;
    private final List<MetadataKey<T>> parents;

    @SafeVarargs
    public MetadataKey(String name, MetadataKey<T>... parents) {
        this.name = name;
        this.parents = List.of(parents);
    }

    public List<MetadataKey<T>> getParents() {
        return parents;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "MetadataKey %s".formatted(name);
    }
}

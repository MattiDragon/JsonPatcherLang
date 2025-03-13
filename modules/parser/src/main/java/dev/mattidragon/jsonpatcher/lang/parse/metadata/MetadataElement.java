package dev.mattidragon.jsonpatcher.lang.parse.metadata;

// TODO: Attach position as tree metadata
public sealed interface MetadataElement
        permits MetadataArray, MetadataBoolean, MetadataNull, MetadataNumber, MetadataObject, MetadataString {
}

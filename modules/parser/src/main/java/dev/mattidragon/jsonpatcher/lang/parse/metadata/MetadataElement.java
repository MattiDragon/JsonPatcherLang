package dev.mattidragon.jsonpatcher.lang.parse.metadata;

public sealed interface MetadataElement
        permits MetadataArray, MetadataBoolean, MetadataNull, MetadataNumber, MetadataObject, MetadataString {
}

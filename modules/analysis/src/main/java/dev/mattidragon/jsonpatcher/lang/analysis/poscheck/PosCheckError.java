package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

public sealed interface PosCheckError permits IllegalPosNestingError, IllegalSourceSpanError, MissingMetadataError {
    String message();
}

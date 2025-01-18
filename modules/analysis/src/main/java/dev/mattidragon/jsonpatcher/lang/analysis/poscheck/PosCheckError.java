package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;

sealed interface PosCheckError extends Diagnostic permits IllegalPosNestingError, IllegalSourceSpanError, MissingMetadataError {
    @Override
    default Kind kind() {
        return Kind.INTERNAL_ERROR;
    }
}

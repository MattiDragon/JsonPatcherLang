package io.github.mattidragon.jsonpatcher.docs.data;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.jetbrains.annotations.Nullable;

public sealed interface DocEntry {
    String description();
    String name();
    
    record Module(String name, String location, String description, @Nullable SourceSpan namePos) implements DocEntry {}
    record Type(String name, DocType definition, String description, @Nullable SourceSpan namePos) implements DocEntry {}
    record Value(String owner, String name, DocType definition, String description, @Nullable SourceSpan ownerPos, @Nullable SourceSpan namePos) implements DocEntry {}
}

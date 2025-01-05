package dev.mattidragon.jsonpatcher.docs.data;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

public sealed interface DocEntry {
    String description();
    String name();
    @Nullable SourceSpan namePos();
    
    record Module(String name, String location, String description, @Nullable SourceSpan namePos, @Nullable SourceSpan locationPos) implements DocEntry {}
    record Type(String name, DocType definition, String description, @Nullable SourceSpan namePos) implements DocEntry {}
    record Value(String owner, String name, DocType definition, String description, @Nullable SourceSpan ownerPos, @Nullable SourceSpan namePos) implements DocEntry {}
}

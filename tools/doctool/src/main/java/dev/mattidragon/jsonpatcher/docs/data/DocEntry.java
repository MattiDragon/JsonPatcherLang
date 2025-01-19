package dev.mattidragon.jsonpatcher.docs.data;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

import java.util.List;

public sealed interface DocEntry {
    String description();
    String name();
    @Nullable SourceSpan namePos();

    sealed interface Global extends DocEntry {
        List<String> requiredMetadata();
        SourceSpan namePos();
    }

    record Module(String name, String location, String description, @Nullable SourceSpan namePos, @Nullable SourceSpan locationPos) implements DocEntry {}
    record Type(String name, DocType definition, String description, @Nullable SourceSpan namePos) implements DocEntry {}
    record Value(String owner, String name, DocType definition, String description, @Nullable SourceSpan ownerPos, @Nullable SourceSpan namePos) implements DocEntry {}
    record GlobalValue(String name, DocType definition, String description, @Nullable SourceSpan namePos, List<String> requiredMetadata) implements Global {}
    record GlobalModule(String name, String description, SourceSpan namePos, List<String> requiredMetadata) implements Global {}
}

package dev.mattidragon.jsonpatcher.docs.tree;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import org.jspecify.annotations.Nullable;

public record DocTreeProperty(DocEntry.PropertyEntry entry, @Nullable TreeMetadata metadata) {
}

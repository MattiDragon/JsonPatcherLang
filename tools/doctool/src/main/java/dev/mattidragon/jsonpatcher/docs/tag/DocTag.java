package dev.mattidragon.jsonpatcher.docs.tag;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;
import org.commonmark.node.Node;

import java.util.List;

public record DocTag(String name, String content, List<Node> formattedContent) implements MetadataHolder {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return List.of();
    }
}

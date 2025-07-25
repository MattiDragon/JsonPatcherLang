package dev.mattidragon.jsonpatcher.docs.tag;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.commonmark.node.Code;
import org.commonmark.node.Node;

import java.util.List;

/**
 * A fallback tag processor that handles all tags not handled by other processors.
 * Not registered as a service in order to let other processors take precedence.
 */
public final class FallbackProcessor implements TagProcessor {
    public static final FallbackProcessor INSTANCE = new FallbackProcessor();

    private FallbackProcessor() {
    }

    @Override
    public boolean accepts(String tagType) {
        return true;
    }

    @Override
    public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        return List.of(new Code("@%s: %s".formatted(name, content.value())));
    }
}

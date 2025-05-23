package dev.mattidragon.jsonpatcher.docs.tag;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.commonmark.node.Node;

import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * A service which processes doc tags. Used to determine the formatting of the tag in docs output.
 * Processors may also add metadata to tags to signify their actual value in a parsed form.
 */
public interface TagProcessor {
    /**
     * A collection of all registered tag processors.
     */
    Collection<TagProcessor> ALL =
            StreamSupport.stream(ServiceLoader.load(TagProcessor.class).spliterator(), false).toList();

    /**
     * A tag processor that accepts all tags and delegates to the first processor that accepts the tag.
     */
    TagProcessor COMBINED = new TagProcessor() {
        @Override
        public boolean accepts(String tagType) {
            return true;
        }

        @Override
        public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
            for (var processor : ALL) {
                if (processor.accepts(name.value())) {
                    return processor.process(name, content, entry, metadata, diagnostics);
                }
            }
            diagnostics.addDiagnostic(new DocParseDiagnostic(name.pos(), "Unknown doc tag: " + name.value(), DocParseDiagnostic.Type.UNKNOWN_TAG));
            return FallbackProcessor.INSTANCE.process(name, content, entry, metadata, diagnostics);
        }
    };

    /**
     * Checks if this processor can handle the given tag type.
     * If this method returns {@code false}, the processor will not be used for this tag.
     * @param tagType The tag type to check
     * @return {@code true} if this processor can handle the tag type, {@code false} otherwise
     */
    boolean accepts(String tagType);

    /**
     * Processes the given tag and returns a node representing the processed tag.
     * The node will be used to generate the documentation output.
     * This method may also modify the metadata of the tag or entry to store the contents of the tag for other code.
     *
     * @param name        The tag type, e.g. "method", "param", etc.
     * @param content     The content of the tag, e.g. "number", "string", etc.
     * @param entry       The entry the tag is attached to.
     * @param metadata    Tree metadata that can be used to attach metadata to the entry.
     * @param diagnostics Diagnostics builder to report errors and warnings.
     * @return Markdown nodes representing the content of the tag. Should usually only consist of inline elements.
     */
    List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics);
}

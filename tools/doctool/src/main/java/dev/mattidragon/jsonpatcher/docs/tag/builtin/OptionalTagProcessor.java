package dev.mattidragon.jsonpatcher.docs.tag.builtin;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.docs.tag.PositionedString;
import dev.mattidragon.jsonpatcher.docs.tag.TagProcessor;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

import java.util.List;

public final class OptionalTagProcessor implements TagProcessor {
    public static final MetadataKey<Boolean> OPTIONAL = new MetadataKey<>("OptionalTag/OPTIONAL");

    @Override
    public boolean accepts(String tagType) {
        return tagType.equals("optional");
    }

    @Override
    public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        if (!content.value().isBlank()) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(
                    content.pos(),
                    "Optional tag should not have any content",
                    DocParseDiagnostic.Type.INVALID_TAG
            ));
        }
        if (!(entry instanceof DocEntry.PropertyEntry)) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(
                    name.pos(),
                    "Optional tag can only be used on properties",
                    DocParseDiagnostic.Type.INVALID_TAG
            ));
        }

        metadata.put(entry, OPTIONAL, true);

        var em = new Emphasis();
        em.appendChild(new Text("This property is optional and may not be present"));
        return List.of(em);
    }
}

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

public final class HardcodedTypeTagProcessor implements TagProcessor {
    public static final MetadataKey<Kind> KIND = new MetadataKey<>("HardcodedTypeTag/KIND");

    @Override
    public boolean accepts(String tagType) {
        return tagType.equals("special_type");
    }

    @Override
    public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var kind = switch (content.value().trim()) {
            case "function_bind" -> Kind.FUNCTION_BIND;
            case "function_chain" -> Kind.FUNCTION_CHAIN;
            default -> {
                diagnostics.addDiagnostic(new DocParseDiagnostic(
                        content.pos(),
                        "Invalid special type tag value: " + content,
                        DocParseDiagnostic.Type.INVALID_TAG));
                yield null;
            }
        };
        if (kind != null) {
            metadata.put(entry, KIND, kind);
        }

        var em = new Emphasis();
        em.appendChild(new Text("This property has special behaviour in the typechecker"));
        return List.of(em);
    }

    public enum Kind {
        FUNCTION_BIND,
        FUNCTION_CHAIN
    }
}

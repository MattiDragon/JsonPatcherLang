package dev.mattidragon.jsonpatcher.docs.tag.builtin;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.docs.tag.PositionedString;
import dev.mattidragon.jsonpatcher.docs.tag.TagProcessor;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.commonmark.node.Emphasis;
import org.commonmark.node.Node;
import org.commonmark.node.Text;

import java.util.List;

public final class MethodTagProcessor implements TagProcessor {
    public static final MetadataKey<ValueType> METHOD_TYPE = new MetadataKey<>("MethodTag/METHOD_TYPE");

    @Override
    public boolean accepts(String tagType) {
        return tagType.equals("method");
    }

    @Override
    public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var type = switch (content.value().trim()) {
            case "number" -> ValueType.NUMBER;
            case "string" -> ValueType.STRING;
            case "boolean" -> ValueType.BOOLEAN;
            case "array" -> ValueType.ARRAY;
            case "function" -> ValueType.FUNCTION;
            default -> {
                var pos = content.pos();
                diagnostics.addDiagnostic(new DocParseDiagnostic(
                        pos,
                        "Invalid method tag value: " + content,
                        DocParseDiagnostic.Type.INVALID_TAG));
                yield null;
            }
        };

        if (type != null) {
            metadata.put(entry, METHOD_TYPE, type);
        }

        var description = switch (content.value().trim()) {
            case "number" -> "Available as a method on numbers";
            case "string" -> "Available as a method on strings";
            case "boolean" -> "Available as a method on booleans";
            case "array" -> "Available as a method on arrays";
            case "function" -> "Available as a method on functions";
            default -> "INCORRECT TAG VALUE";
        };

        var em = new Emphasis();
        em.appendChild(new Text(description));
        return List.of(em);
    }
}

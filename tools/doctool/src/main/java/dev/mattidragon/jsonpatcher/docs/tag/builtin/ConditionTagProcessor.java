package dev.mattidragon.jsonpatcher.docs.tag.builtin;

import dev.mattidragon.jsonpatcher.docs.data.DocCondition;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.DocConditionParser;
import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.docs.parse.Tokenizer;
import dev.mattidragon.jsonpatcher.docs.tag.PositionedString;
import dev.mattidragon.jsonpatcher.docs.tag.TagProcessor;
import dev.mattidragon.jsonpatcher.docs.write.DocConditionWriter;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.commonmark.node.Code;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;

import java.util.List;

public class ConditionTagProcessor implements TagProcessor {
    public static final MetadataKey<DocCondition> CONDITION = new MetadataKey<>("ConditionTag/CONDITION");

    @Override
    public boolean accepts(String tagType) {
        return tagType.equals("requires");
    }

    @Override
    public List<Node> process(PositionedString name, PositionedString content, DocEntry entry, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        var tokenizer = new Tokenizer(content.value(), content.pos().from());
        var condition = DocConditionParser.parse(tokenizer, metadata, diagnostics);

        if (tokenizer.hasNext()) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(content.pos(), "Trailing content after condition", DocParseDiagnostic.Type.CONDITION_PARSE_ERROR));
        }

        metadata.put(entry, CONDITION, condition);

        var strong = new StrongEmphasis();
        strong.appendChild(new Text("Requires"));
        return List.of(strong, new Text(" "), new Code(DocConditionWriter.write(condition)));
    }
}

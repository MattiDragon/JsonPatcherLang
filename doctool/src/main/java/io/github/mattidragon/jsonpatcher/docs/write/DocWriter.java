package io.github.mattidragon.jsonpatcher.docs.write;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.data.DocType;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class DocWriter {
    public static final List<Extension> DEFAULT_EXTENSIONS = List.of(TablesExtension.create(), StrikethroughExtension.create());
    public static final Parser DEFAULT_PARSER = Parser.builder().extensions(DEFAULT_EXTENSIONS).build();
    private final List<OutputType> types = new ArrayList<>();
    private final List<OutputModule> modules = new ArrayList<>();

    private int headingLevel = 1;
    private Parser parser = DEFAULT_PARSER;

    public DocWriter(List<DocEntry> entries) {
        var values = new ArrayList<DocEntry.Value>();
        
        for (var entry : entries) {
            switch (entry) {
                case DocEntry.Module module -> modules.add(new OutputModule(module, new ArrayList<>()));
                case DocEntry.Type type -> types.add(new OutputType(type, new ArrayList<>()));
                case DocEntry.Value value -> values.add(value);
            }
        }

        var owners = new LinkedHashMap<String, Owner>();
        types.forEach(type -> owners.put(type.entry().name(), type));
        modules.forEach(module -> owners.put(module.entry().name(), module));

        for (var value : values) {
            var owner = owners.get(value.owner());
            // If we don't have an owner, generate a dummy
            if (owner == null) {
                var type = new OutputType(new DocEntry.Type(value.owner(), DocType.Special.UNKNOWN, ""), new ArrayList<>()); 
                owner = type;
                types.add(type);
            }
            owner.values().add(value);
        }
    }

    public void setHeadingLevel(int headingLevel) {
        if (headingLevel < 1) throw new IllegalArgumentException("Heading level must be positive");
        this.headingLevel = headingLevel;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public void buildDocument(Node document) {
        for (var module : modules) {
            writeHeader(document, "Module", module.entry().name(), headingLevel);
            document.appendChild(parser.parse(module.entry().description()));
            writeValues(document, module.values());
        }
        for (var type : types) {
            writeHeader(document, "Type", type.entry().name(), headingLevel);
            writeTypeDefinition(document, type.entry().definition());
            document.appendChild(parser.parse(type.entry().description()));
            writeValues(document, type.values());
        }
    }
    
    private void writeValues(Node document, List<DocEntry.Value> values) {
        for (var value : values) {
            var heading = value.definition() instanceof DocType.Function || value.definition() == DocType.Special.FUNCTION ? "Function" : "Property";
            writeHeader(document, heading, value.owner() + "." + value.name(), headingLevel + 1);
            writeTypeDefinition(document, value.definition());
            document.appendChild(parser.parse(value.description()));
        }
    }

    private void writeHeader(Node document, String heading, String name, int level) {
        var moduleHead = new Paragraph();
        moduleHead.appendChild(new Text(heading + " "));
        moduleHead.appendChild(new Code(name));
        document.appendChild(heading(level, moduleHead));
    }

    private void writeTypeDefinition(Node document, DocType definition) {
        var block = new Paragraph();
        var emphasis = new Emphasis();
        if (definition != DocType.Special.UNKNOWN) {
            emphasis.appendChild(new Text("Definition: "));
            emphasis.appendChild(new Code(definition.format()));
        } else {
            emphasis.appendChild(new Text("Definition unknown"));
        }
        block.appendChild(emphasis);
        document.appendChild(block);
    }

    private Node heading(int level, Node content) {
        var heading = new Heading();
        heading.setLevel(level);
        heading.appendChild(content);
        return heading;
    }

    sealed interface Owner {
        List<DocEntry.Value> values();
    }
    
    record OutputModule(DocEntry.Module entry, List<DocEntry.Value> values) implements Owner {}
    record OutputType(DocEntry.Type entry, List<DocEntry.Value> values) implements Owner {}
}

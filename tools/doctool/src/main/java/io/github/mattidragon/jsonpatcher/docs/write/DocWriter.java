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
    private boolean inlineDefinitions = false;
    private boolean valueSubHeaders = true;

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
                var type = new OutputType(new DocEntry.Type(value.owner(), new DocType.Special(DocType.SpecialKind.UNKNOWN, null), "", null), new ArrayList<>()); 
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

    public void setInlineDefinitions(boolean inlineDefinitions) {
        this.inlineDefinitions = inlineDefinitions;
    }

    public void setValueSubHeaders(boolean valueSubHeaders) {
        this.valueSubHeaders = valueSubHeaders;
    }

    public void buildDocument(Node document) {
        for (var module : modules) {
            writeEntry(document, module.entry());
            writeValues(document, module.values());
        }
        for (var type : types) {
            writeEntry(document, type.entry());
            writeValues(document, type.values());
        }
    }
    
    public void writeEntry(Node document, DocEntry entry) {
        switch (entry) {
            case DocEntry.Module module -> {
                writeHeader(document, "Module", module.name(), "", headingLevel);
                addLocationData(document, module);
                document.appendChild(parser.parse(module.description()));
            }
            case DocEntry.Type type -> {
                writeHeader(document, "Type", type.name(), type.definition().format(), headingLevel);
                writeTypeDefinition(document, type.definition());
                document.appendChild(parser.parse(type.description()));
            }
            case DocEntry.Value value -> {
                var heading = value.definition().isFunction() ? "Function" : "Property";
                writeHeader(document, heading, value.owner() + "." + value.name(), value.definition().format(), valueSubHeaders ? headingLevel + 1 : headingLevel);
                writeTypeDefinition(document, value.definition());
                document.appendChild(parser.parse(value.description()));
            }
        }
    }

    private static void addLocationData(Node document, DocEntry.Module entry) {
        if (entry.location() == null) return;
        
        var location = new Paragraph();
        var emp = new Emphasis();
        emp.appendChild(new Text("Available at "));
        emp.appendChild(new Code("\"" + entry.location() + "\""));
        location.appendChild(emp);
        document.appendChild(location);
    }

    private void writeValues(Node document, List<DocEntry.Value> values) {
        for (var value : values) {
            writeEntry(document, value);
        }
    }

    private void writeHeader(Node document, String heading, String name, String definition, int level) {
        var moduleHead = new Paragraph();
        moduleHead.appendChild(new Text(heading + " "));
        moduleHead.appendChild(new Code(name));
        if (inlineDefinitions && !definition.isBlank()) {
            moduleHead.appendChild(new Text(": "));
            moduleHead.appendChild(new Code(definition));
        }
        document.appendChild(heading(level, moduleHead));
    }

    private void writeTypeDefinition(Node document, DocType definition) {
        if (inlineDefinitions) return;
        
        var block = new Paragraph();
        var emphasis = new Emphasis();

        if (definition instanceof DocType.Special special && special.kind() == DocType.SpecialKind.UNKNOWN) {
            emphasis.appendChild(new Text("Definition unknown"));
        } else {
            emphasis.appendChild(new Text("Definition: "));
            emphasis.appendChild(new Code(definition.format()));
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

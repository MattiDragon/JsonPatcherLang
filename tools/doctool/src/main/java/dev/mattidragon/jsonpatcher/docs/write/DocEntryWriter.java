package dev.mattidragon.jsonpatcher.docs.write;

import dev.mattidragon.jsonpatcher.docs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.docs.type.FunctionDocType;
import dev.mattidragon.jsonpatcher.docs.type.NewDocType;
import org.commonmark.node.*;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class DocEntryWriter {
    public static void write(Node document, NewDocEntry entry, int headingLevel) {
        writeHeader(document, entry, headingLevel);
        writeMetadata(document, entry);
        writeBody(document, entry);
    }

    private static void writeHeader(Node document, NewDocEntry entry, int headingLevel) {
        var heading = new Heading();
        heading.setLevel(headingLevel);
        heading.appendChild(new Text(entryType(entry) + " "));
        heading.appendChild(new Code(name(entry)));
        if (entry instanceof NewDocEntry.TypeDeclarationEntry typeDeclarationEntry) {
            heading.appendChild(new Text(" of " + typeDeclarationEntry.baseType().name().toLowerCase(Locale.ROOT)));
        }
        document.appendChild(heading);
    }

    private static void writeMetadata(Node document, NewDocEntry entry) {
        var metadata = new Paragraph();

        if (entry instanceof NewDocEntry.TypeAliasEntry typeAliasEntry) {
            var strong = new StrongEmphasis();
            strong.appendChild(new Text("="));
            metadata.appendChild(strong);
            metadata.appendChild(new Text(" "));
            metadata.appendChild(new Code(DocTypeWriter.write(typeAliasEntry.definition())));
            metadata.appendChild(new HardLineBreak());
        }

        if (entry instanceof NewDocEntry.LibraryEntry libraryEntry && libraryEntry.location().isPresent()) {
            var strong = new StrongEmphasis();
            strong.appendChild(new Text("Location:"));
            metadata.appendChild(strong);
            metadata.appendChild(new Text(" "));
            metadata.appendChild(new Code(libraryEntry.location().get()));
            metadata.appendChild(new HardLineBreak());
        }

        var type = type(entry);
        if (type != null) {
            var strong = new StrongEmphasis();
            strong.appendChild(new Text("Type:"));
            metadata.appendChild(strong);
            metadata.appendChild(new Text(" "));
            metadata.appendChild(new Code(DocTypeWriter.write(type)));
            metadata.appendChild(new HardLineBreak());
        }

        entry.condition().ifPresent(condition -> {
            var strong = new StrongEmphasis();
            strong.appendChild(new Text("Requires:"));
            metadata.appendChild(strong);
            metadata.appendChild(new Text(" "));
            metadata.appendChild(new Code(DocConditionWriter.write(condition)));
            metadata.appendChild(new HardLineBreak());
        });

        document.appendChild(metadata);
    }

    private static void writeBody(Node document, NewDocEntry entry) {
        var body = DocWriter.PARSER.parse(entry.body());
        document.appendChild(body);
    }

    private static @Nullable NewDocType type(NewDocEntry entry) {
        return switch (entry) {
            case NewDocEntry.GlobalValueEntry globalValueEntry
                -> globalValueEntry.type();
            case NewDocEntry.PropertyEntry propertyEntry
                -> propertyEntry.type();
            case NewDocEntry.MetadataEntry metadataEntry
                -> metadataEntry.type();
            default -> null;
        };
    }

    private static String name(NewDocEntry entry) {
        var namespace = String.join(".", entry.namespace().parts());
        if (!namespace.isEmpty()) {
            namespace += ".";
        }
        if (entry instanceof NewDocEntry.PropertyEntry propertyEntry) {
            return namespace + propertyEntry.owner() + "." + entry.name();
        }
        return namespace + entry.name();
    }

    private static String entryType(NewDocEntry entry) {
        return switch (entry) {
            case NewDocEntry.GlobalLibraryEntry globalLibraryEntry -> "Global library";
            case NewDocEntry.GlobalValueEntry globalValueEntry -> "Global variable";
            case NewDocEntry.LibraryEntry libraryEntry -> "Library";
            case NewDocEntry.MetadataEntry metadataEntry -> "Metadata tag";
            case NewDocEntry.NamespaceEntry namespaceEntry -> "Namespace";
            case NewDocEntry.PropertyEntry propertyEntry ->
                    propertyEntry.type() instanceof FunctionDocType ? "Function" : "Property";
            case NewDocEntry.TypeAliasEntry typeAliasEntry -> "Type alias";
            case NewDocEntry.TypeDeclarationEntry typeDeclarationEntry -> "Type";
        };
    }

}

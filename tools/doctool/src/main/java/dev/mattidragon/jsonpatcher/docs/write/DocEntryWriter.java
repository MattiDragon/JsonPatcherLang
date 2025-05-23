package dev.mattidragon.jsonpatcher.docs.write;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.type.DocType;
import dev.mattidragon.jsonpatcher.docs.type.FunctionDocType;
import org.commonmark.node.*;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public class DocEntryWriter {
    public static void write(Node document, DocEntry entry, int headingLevel) {
        writeHeader(document, entry, headingLevel);
        writeMetadata(document, entry);
        writeBody(document, entry);
    }

    private static void writeHeader(Node document, DocEntry entry, int headingLevel) {
        var heading = new Heading();
        heading.setLevel(headingLevel);
        heading.appendChild(new Text(entryType(entry) + " "));
        heading.appendChild(new Code(name(entry)));
        if (entry instanceof DocEntry.TypeDeclarationEntry typeDeclarationEntry) {
            heading.appendChild(new Text(" of " + typeDeclarationEntry.baseType().name().toLowerCase(Locale.ROOT)));
        }
        document.appendChild(heading);
    }

    private static void writeMetadata(Node document, DocEntry entry) {
        var metadata = new Paragraph();

        if (entry instanceof DocEntry.TypeAliasEntry typeAliasEntry) {
            var strong = new StrongEmphasis();
            strong.appendChild(new Text("="));
            metadata.appendChild(strong);
            metadata.appendChild(new Text(" "));
            metadata.appendChild(new Code(DocTypeWriter.write(typeAliasEntry.definition())));
            metadata.appendChild(new HardLineBreak());
        }

        if (entry instanceof DocEntry.LibraryEntry libraryEntry && libraryEntry.location().isPresent()) {
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

        for (var tag : entry.sharedData().tags()) {
            tag.formattedContent().forEach(metadata::appendChild);
            metadata.appendChild(new HardLineBreak());
        }

        document.appendChild(metadata);
    }

    private static void writeBody(Node document, DocEntry entry) {
        var body = DocWriter.PARSER.parse(entry.body());
        document.appendChild(body);
    }

    private static @Nullable DocType type(DocEntry entry) {
        return switch (entry) {
            case DocEntry.GlobalValueEntry globalValueEntry
                -> globalValueEntry.type();
            case DocEntry.PropertyEntry propertyEntry
                -> propertyEntry.type();
            case DocEntry.MetadataEntry metadataEntry
                -> metadataEntry.type();
            default -> null;
        };
    }

    private static String name(DocEntry entry) {
        var namespace = String.join(".", entry.namespace().parts());
        if (!namespace.isEmpty()) {
            namespace += ".";
        }
        if (entry instanceof DocEntry.PropertyEntry propertyEntry) {
            return namespace + propertyEntry.owner() + "." + entry.name();
        }
        return namespace + entry.name();
    }

    private static String entryType(DocEntry entry) {
        return switch (entry) {
            case DocEntry.GlobalLibraryEntry globalLibraryEntry -> "Global library";
            case DocEntry.GlobalValueEntry globalValueEntry -> "Global variable";
            case DocEntry.LibraryEntry libraryEntry -> "Library";
            case DocEntry.MetadataEntry metadataEntry -> "Metadata tag";
            case DocEntry.NamespaceEntry namespaceEntry -> "Namespace";
            case DocEntry.PropertyEntry propertyEntry ->
                    propertyEntry.type() instanceof FunctionDocType ? "Function" : "Property";
            case DocEntry.TypeAliasEntry typeAliasEntry -> "Type alias";
            case DocEntry.TypeDeclarationEntry typeDeclarationEntry -> "Type";
        };
    }

}

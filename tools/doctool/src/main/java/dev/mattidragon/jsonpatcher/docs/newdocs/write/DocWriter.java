package dev.mattidragon.jsonpatcher.docs.newdocs.write;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.docs.newdocs.tree.DocTree;
import dev.mattidragon.jsonpatcher.docs.newdocs.tree.DocTreeNamespace;
import dev.mattidragon.jsonpatcher.docs.newdocs.tree.DocTreeObject;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Code;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class DocWriter {
    static final Parser PARSER = Parser.builder().extensions(List.of(TablesExtension.create(), StrikethroughExtension.create())).build();

    public static void write(Node node, DocTree docTree, int headingLevel) {
        var activeNamespaces = new ArrayList<>(docTree.namespaces().values());
        activeNamespaces.sort(Comparator.comparing(DocTreeNamespace::description));

        for (var namespace : activeNamespaces) {
            int innerHeadingLevel;
            if (namespace.description().parts().isEmpty()) {
                innerHeadingLevel = headingLevel;
            } else {
                DocEntryWriter.write(node, getEntry(namespace), headingLevel);
                innerHeadingLevel = headingLevel + 1;
            }

            namespace.objects()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(DocTreeObject::name))
                    .forEach(object -> {
                        var objectEntry = object.entry();
                        if (objectEntry != null) {
                            DocEntryWriter.write(node, objectEntry, innerHeadingLevel);
                        } else {
                            writeUnknownEntry(node, object.name(), innerHeadingLevel);
                        }

                        for (var value : object.properties().values()) {
                            DocEntryWriter.write(node, value.entry(), innerHeadingLevel + 1);
                        }
                    });
        }
    }

    private static void writeUnknownEntry(Node node, String name, int headingLevel) {
        var heading = new Heading();
        heading.setLevel(headingLevel);
        heading.appendChild(new Text("Unknown object "));
        heading.appendChild(new Code(name));
        node.appendChild(heading);
    }

    private static NewDocEntry.@NotNull NamespaceEntry getEntry(DocTreeNamespace namespace) {
        var namespaceEntry = namespace.entry();
        if (namespaceEntry != null) {
            return namespaceEntry;
        }
        return new NewDocEntry.NamespaceEntry(
                namespace.description().withoutLast(),
                namespace.description().parts().isEmpty()
                        ? ""
                        : namespace.description().parts().getLast(),
                Optional.empty(),
                ""
        );
    }
}

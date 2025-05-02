import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.doctool {
    requires static org.jspecify;
    requires static org.jetbrains.annotations;

    requires org.commonmark;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.gfm.tables;

    requires jsonpatcher.lang.parser;

    exports dev.mattidragon.jsonpatcher.docs;
    exports dev.mattidragon.jsonpatcher.docs.data;
    exports dev.mattidragon.jsonpatcher.docs.parse;
    exports dev.mattidragon.jsonpatcher.docs.write;

    exports dev.mattidragon.jsonpatcher.docs.newdocs;
    exports dev.mattidragon.jsonpatcher.docs.newdocs.data;
    exports dev.mattidragon.jsonpatcher.docs.newdocs.tree;
    exports dev.mattidragon.jsonpatcher.docs.newdocs.type;
    exports dev.mattidragon.jsonpatcher.docs.newdocs.write;
}
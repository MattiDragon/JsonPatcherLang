import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.doctool {
    requires jsonpatcher.lang.parser;
    requires org.commonmark;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.gfm.tables;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.docs;
    exports dev.mattidragon.jsonpatcher.docs.data;
    exports dev.mattidragon.jsonpatcher.docs.parse;
    exports dev.mattidragon.jsonpatcher.docs.write;
}
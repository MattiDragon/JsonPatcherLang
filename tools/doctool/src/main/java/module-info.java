import dev.mattidragon.jsonpatcher.docs.tag.TagProcessor;
import dev.mattidragon.jsonpatcher.docs.tag.builtin.ConditionTagProcessor;
import dev.mattidragon.jsonpatcher.docs.tag.builtin.MethodTagProcessor;
import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.doctool {
    uses TagProcessor;
    provides TagProcessor with MethodTagProcessor, ConditionTagProcessor;

    requires static org.jspecify;
    requires static org.jetbrains.annotations;

    requires org.commonmark;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.gfm.tables;

    requires jsonpatcher.lang.parser;

    exports dev.mattidragon.jsonpatcher.docs.data;
    exports dev.mattidragon.jsonpatcher.docs.tree;
    exports dev.mattidragon.jsonpatcher.docs.type;
    exports dev.mattidragon.jsonpatcher.docs.write;
    exports dev.mattidragon.jsonpatcher.docs.tag;
    exports dev.mattidragon.jsonpatcher.docs.tag.builtin;
    exports dev.mattidragon.jsonpatcher.docs;
}
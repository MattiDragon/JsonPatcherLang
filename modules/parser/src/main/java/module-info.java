import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.parser {
    requires transitive jsonpatcher.lang.ast;
    requires org.jetbrains.annotations;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang.parse;
    exports dev.mattidragon.jsonpatcher.lang.parse.metadata;
}
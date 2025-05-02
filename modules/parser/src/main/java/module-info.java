import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.parser {
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    requires transitive jsonpatcher.lang.ast;

    exports dev.mattidragon.jsonpatcher.lang.parse;
    exports dev.mattidragon.jsonpatcher.lang.parse.metadata;
}
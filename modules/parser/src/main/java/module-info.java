import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.parser {
    requires jsonpatcher.lang.shared;
    requires org.jetbrains.annotations;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang.parse;
}
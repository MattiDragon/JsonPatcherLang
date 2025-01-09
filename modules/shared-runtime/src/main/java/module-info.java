import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime.shared {
    uses Runtime;
    requires org.jspecify;
    requires transitive jsonpatcher.lang.ast;

    exports dev.mattidragon.jsonpatcher.lang.runtime;
    exports dev.mattidragon.jsonpatcher.lang.runtime.stdlib;
}
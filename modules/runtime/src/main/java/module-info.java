import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime {
    requires org.jspecify;
    requires org.jetbrains.annotations;

    requires transitive jsonpatcher.lang.runtime.shared;
    requires jsonpatcher.lang.compiler;
    requires jsonpatcher.lang.parser;

    exports dev.mattidragon.jsonpatcher.lang.runtime;
    exports dev.mattidragon.jsonpatcher.lang.runtime.environment;
    exports dev.mattidragon.jsonpatcher.lang.runtime.generated;
    exports dev.mattidragon.jsonpatcher.lang.runtime.hooks;
}
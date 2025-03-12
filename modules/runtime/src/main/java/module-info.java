import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime {
    requires org.jspecify;
    requires org.jetbrains.annotations;

    requires jsonpatcher.lang.compiler;
    requires jsonpatcher.lang.parser;

    exports dev.mattidragon.jsonpatcher.lang.runtime;
    exports dev.mattidragon.jsonpatcher.lang.runtime.environment;
    exports dev.mattidragon.jsonpatcher.lang.runtime.generated;
    exports dev.mattidragon.jsonpatcher.lang.runtime.hooks;
    exports dev.mattidragon.jsonpatcher.lang.runtime.lib.builder;
    exports dev.mattidragon.jsonpatcher.lang.runtime.value;
}
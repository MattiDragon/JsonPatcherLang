import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime {
    uses dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.remap.Remapper;
    requires static org.jspecify;
    requires static org.jetbrains.annotations;

    requires jsonpatcher.lang.compiler;
    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.stdlib;

    exports dev.mattidragon.jsonpatcher.lang.runtime;
    exports dev.mattidragon.jsonpatcher.lang.runtime.environment;
    exports dev.mattidragon.jsonpatcher.lang.runtime.generated;
    exports dev.mattidragon.jsonpatcher.lang.runtime.hooks;
    exports dev.mattidragon.jsonpatcher.lang.runtime.lib.builder;
    exports dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.remap;
    exports dev.mattidragon.jsonpatcher.lang.runtime.value;
}
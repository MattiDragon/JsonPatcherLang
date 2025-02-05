import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime.bytecode {
    requires transitive jsonpatcher.lang.runtime.shared;
    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.analysis;
    requires org.jetbrains.annotations;
    requires org.objectweb.asm;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode;
    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;
    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode.environment;
}
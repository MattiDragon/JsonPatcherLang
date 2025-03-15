import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.compiler {
    requires org.jetbrains.annotations;
    requires org.objectweb.asm;
    requires org.jspecify;

    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.analysis;
    requires jsonpatcher.lang.stdlib;

    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode;
    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;
}
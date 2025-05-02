import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.compiler {
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    requires org.objectweb.asm;

    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.analysis;
    requires jsonpatcher.lang.stdlib;

    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode;
    exports dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;
}
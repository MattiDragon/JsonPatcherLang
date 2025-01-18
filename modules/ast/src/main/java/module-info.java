import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.ast {
    requires org.jspecify;
    requires org.jetbrains.annotations;

    exports dev.mattidragon.jsonpatcher.lang.ast;
    exports dev.mattidragon.jsonpatcher.lang.ast.expression;
    exports dev.mattidragon.jsonpatcher.lang.ast.function;
    exports dev.mattidragon.jsonpatcher.lang.ast.meta;
    exports dev.mattidragon.jsonpatcher.lang.ast.statement;
    exports dev.mattidragon.jsonpatcher.lang.error;
}
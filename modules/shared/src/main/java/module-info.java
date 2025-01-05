import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.shared {
    uses Runtime;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang;

    exports dev.mattidragon.jsonpatcher.lang.analysis.constant;
    exports dev.mattidragon.jsonpatcher.lang.analysis.variable;

    exports dev.mattidragon.jsonpatcher.lang.ast;
    exports dev.mattidragon.jsonpatcher.lang.ast.expression;
    exports dev.mattidragon.jsonpatcher.lang.ast.function;
    exports dev.mattidragon.jsonpatcher.lang.ast.meta;
    exports dev.mattidragon.jsonpatcher.lang.ast.statement;

    exports dev.mattidragon.jsonpatcher.lang.runtime;
    exports dev.mattidragon.jsonpatcher.lang.runtime.stdlib;
}
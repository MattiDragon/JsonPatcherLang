import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.analysis {
    requires static org.jspecify;
    requires transitive jsonpatcher.lang.ast;
    requires transitive jsonpatcher.lang.parser;

    exports dev.mattidragon.jsonpatcher.lang.analysis.constant;
    exports dev.mattidragon.jsonpatcher.lang.analysis.variable;
    exports dev.mattidragon.jsonpatcher.lang.analysis.poscheck;
    exports dev.mattidragon.jsonpatcher.lang.analysis.typecheck;
    exports dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;
    exports dev.mattidragon.jsonpatcher.lang.analysis.comment;
    exports dev.mattidragon.jsonpatcher.lang.analysis.typecheck.v2;
}
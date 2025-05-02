import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.analysis {
    requires static org.jspecify;
    requires transitive jsonpatcher.lang.ast;

    exports dev.mattidragon.jsonpatcher.lang.analysis.constant;
    exports dev.mattidragon.jsonpatcher.lang.analysis.variable;
    exports dev.mattidragon.jsonpatcher.lang.analysis.poscheck;
}
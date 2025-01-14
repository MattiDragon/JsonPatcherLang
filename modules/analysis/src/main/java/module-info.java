import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.analysis {
    requires org.jspecify;
    requires transitive jsonpatcher.lang.ast;
    requires transitive jsonpatcher.lang.runtime.shared;

    exports dev.mattidragon.jsonpatcher.lang.analysis.constant;
    exports dev.mattidragon.jsonpatcher.lang.analysis.variable;
    exports dev.mattidragon.jsonpatcher.lang.analysis.poscheck;
}
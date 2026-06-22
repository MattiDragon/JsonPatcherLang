import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.toolcommon {
    exports dev.mattidragon.jsonpatcher.toolcommon.typing;
    requires static org.jspecify;

    requires transitive jsonpatcher.lang.analysis;
    requires transitive jsonpatcher.tools.doctool;
    requires transitive jsonpatcher.lang.stdlib;
}

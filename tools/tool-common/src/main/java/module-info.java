import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.toolcommon {
    exports dev.mattidragon.jsonpatcher.toolcommon.typing;
    requires static org.jspecify;

    requires jsonpatcher.lang.analysis;
    requires jsonpatcher.tools.doctool;
}

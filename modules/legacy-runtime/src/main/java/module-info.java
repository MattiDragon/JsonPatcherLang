import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;
import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.runtime.legacy {
    requires transitive jsonpatcher.lang.runtime.shared;
    requires org.jetbrains.annotations;
    requires org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang.runtime.legacy;

    provides Runtime with dev.mattidragon.jsonpatcher.lang.runtime.legacy.LegacyRuntime;
}
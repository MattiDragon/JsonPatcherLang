import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.lang.stdlib {
    requires static org.jspecify;

    exports dev.mattidragon.jsonpatcher.lang.stdlib;
}
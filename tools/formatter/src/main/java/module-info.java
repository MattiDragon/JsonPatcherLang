import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.formatter {
    requires static org.jspecify;

    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.runtime;
    requires jsonpatcher.lang.analysis;

    // TODO: check which exports we really need
    exports dev.mattidragon.jsonpatcher.formatter.printer;
}

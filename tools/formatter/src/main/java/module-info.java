import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.formatter {
    requires static org.jspecify;

    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.runtime;
}

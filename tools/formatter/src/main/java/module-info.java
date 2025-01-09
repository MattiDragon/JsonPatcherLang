import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.formatter {
    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.runtime.shared;
    requires org.jspecify;
}

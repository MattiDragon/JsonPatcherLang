import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.cli {
    requires jsonpatcher.tools.doctool;
    requires jsonpatcher.tools.formatter;
    requires static org.jspecify;
    requires info.picocli;
    requires jsonpatcher.lang.ast;
    requires jsonpatcher.lang.parser;
    requires org.commonmark;

    exports dev.mattidragon.jsonpatcher.cli;
    opens dev.mattidragon.jsonpatcher.cli to info.picocli;
    opens dev.mattidragon.jsonpatcher.cli.commands to info.picocli;
}

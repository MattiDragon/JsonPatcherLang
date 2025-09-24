import org.jspecify.annotations.NullMarked;

@NullMarked
module jsonpatcher.tools.cli {
    requires static org.jspecify;
    requires static org.jetbrains.annotations;

    requires jsonpatcher.tools.doctool;
    requires jsonpatcher.tools.formatter;
    requires jsonpatcher.tools.toolcommon;
    requires jsonpatcher.lang.ast;
    requires jsonpatcher.lang.parser;
    requires jsonpatcher.lang.analysis;
    requires jsonpatcher.lang.stdlib;
    requires jsonpatcher.lang.runtime;
    requires jsonpatcher.lang.compiler;

    requires info.picocli;
    requires org.commonmark;
    requires com.google.gson;

    exports dev.mattidragon.jsonpatcher.cli;
    opens dev.mattidragon.jsonpatcher.cli to info.picocli;
    opens dev.mattidragon.jsonpatcher.cli.commands to info.picocli;
    opens dev.mattidragon.jsonpatcher.cli.impl to info.picocli;
}

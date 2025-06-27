package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrintOptions;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrinter;
import dev.mattidragon.jsonpatcher.formatter.printer.ProgramPrinter;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.SuppressingCommentDiagnosticFilter;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "format",
        aliases = "fmt",
        description = "Format jsonpatcher source files",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class FormatCommand implements Callable<Integer> {
    @Option(names = {"-c", "--max-columns"},
            description = {
                    "The maximum amount of columns the formatter will stick to in most cases",
                    "Default: 80"
            },
            defaultValue = "80")
    public int maxColumns = 80;

    @Option(names = {"-g", "--ignore-errors"},
            description = "Makes the formatter ignore errors, outputting invalid code")
    public boolean ignoreErrors = false;

    @Option(names = {"-i", "--indent"},
            description = {
                    "Sets the indentation string to use for formatting",
                    "Default: 4 spaces"
            },
            defaultValue = "    ")
    public String indent = "    ";

    @Parameters(index = "0",
            description = "The input jsonpatcher source file location")
    public Path inFile = Path.of(".");

    @Parameters(index = "1",
            description = "The output jsonpatcher source file location")
    public Path outFile = Path.of(".");

    @Override
    public Integer call() throws IOException {
        String code;
        try {
            code = Files.readString(inFile);
        } catch (IOException e) {
            throw new IOException("Failed to read input file " + inFile, e);
        }

        var diagnostics = new DiagnosticsBuilder();
        var commentAttacher = new CommentAttacher();
        var diagnosticFilter = new SuppressingCommentDiagnosticFilter();

        var lex = Lexer.lex(code, inFile.toString(), diagnostics, CommentHandler.allOf(commentAttacher, diagnosticFilter));
        var parse = Parser.parse(lex.tokens(), diagnostics);

        commentAttacher.process(parse.program(), parse.treeMetadata());

        for (var diagnostic : diagnostics.build(diagnosticFilter).all()) {
            System.err.println(diagnostic.toDisplay());
        }

        var printOptions = PrettyPrintOptions.builder();
        printOptions.indent(indent);
        printOptions.maxColumns(maxColumns);
        if (ignoreErrors) {
            printOptions.dontFailOnError();
        }

        var printer = new PrettyPrinter(printOptions.build(), parse.treeMetadata());

        try {
            ProgramPrinter.prettyPrint(parse.program(), parse.metadata(), printer);
        } catch (RuntimeException e) {
            System.err.println("error: Error while formatting:");
            e.printStackTrace(System.err);
            return 1;
        }

        var newCode = printer.getOutput();
        try {
            Files.writeString(outFile, newCode);
        } catch (IOException e) {
            throw new IOException("Failed to write output file " + outFile, e);
        }
        return 0;
    }
}

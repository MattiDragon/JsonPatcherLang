package dev.mattidragon.jsonpatcher.formatter;

import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrintOptions;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrinter;
import dev.mattidragon.jsonpatcher.formatter.printer.ProgramPrinter;
import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    private static final int UNKNOWN_OPTION = 1;
    private static final int MISSING_OPTION = 2;
    private static final int IO_ERROR = 3;
    private static final int PARSE_ERROR = 4;
    private static final int FORMAT_ERROR = 5;

    public static void main(String[] args) {
        var exitCode = 0;
        var ioOptions = new Object() {
            @Nullable String outfile = null;
            boolean stdout = false;
            @Nullable String infile = null;
        };
        var printOptions = PrettyPrintOptions.builder();

        if (args.length == 0) args = new String[]{"--help"};

        String currentOption = null;
        for (var arg : args) {
            if (currentOption != null) {
                switch (currentOption) {
                    case "out" -> ioOptions.outfile = arg;
                    case "in" -> ioOptions.infile = arg;
                    case "col" -> printOptions.maxColumns(Integer.parseUnsignedInt(arg));
                    case "indent" -> printOptions.indent(arg);
                }
                currentOption = null;
            } else {
                switch (arg) {
                    case "-i", "--input" -> currentOption = "in";
                    case "-o", "--output" -> currentOption = "out";
                    case "-O", "--stdout" -> ioOptions.stdout = true;
                    case "-h", "--help", "help", "-?" -> {
                        printHelp();
                        System.exit(exitCode);
                        return;
                    }
                    case "-c", "--max-columns" -> currentOption = "col";
                    case "-g", "--ignore-errors" -> printOptions.dontFailOnError();
                    case "-l", "--indent" -> currentOption = "indent";
                    default -> {
                        System.err.println("error: Unrecognised option: " + arg);
                        exitCode = UNKNOWN_OPTION;
                    }
                }
            }
        }

        if (ioOptions.infile == null) {
            System.err.println("error: Input file must be defined");
            exitCode = MISSING_OPTION;
        }
        if (ioOptions.outfile == null && !ioOptions.stdout) {
            System.err.println("error: Output must be defined");
            exitCode = MISSING_OPTION;
        }

        if (exitCode != 0) {
            System.err.println("Exiting early due to previous errors");
            System.exit(exitCode);
            return;
        }

        String code;
        try {
            code = Files.readString(Path.of(ioOptions.infile));
        } catch (IOException e) {
            System.err.println("error: IO error whilst reading input file:");
            e.printStackTrace(System.err);
            System.exit(IO_ERROR);
            return;
        }

        Parser.Result parseResult;
        try {
            var langConfig = new LangConfig(LangConfig.StackTraceMode.JAVA);
            var lex = Lexer.lex(langConfig, code, ioOptions.infile); // TODO: capture comments and add them back
            if (!lex.errors().isEmpty()) {
                var e = lex.errors().getFirst();
                lex.errors().stream().skip(1).forEach(e::addSuppressed);
                throw e;
            }
            parseResult = Parser.parse(langConfig, lex.tokens());
            if (!parseResult.errors().isEmpty()) {
                var e = parseResult.errors().getFirst();
                parseResult.errors().stream().skip(1).forEach(e::addSuppressed);
                throw e;
            }
        } catch (RuntimeException e) {
            System.err.println("error: Error while parsing input file:");
            e.printStackTrace(System.err);
            System.exit(PARSE_ERROR);
            return;
        }

        var printer = new PrettyPrinter(printOptions.build(), parseResult.treeMetadata());
        try {
            ProgramPrinter.prettyPrint(parseResult.program(), parseResult.metadata(), printer);
        } catch (RuntimeException e) {
            System.err.println("error: Error while formatting:");
            e.printStackTrace(System.err);
            System.exit(FORMAT_ERROR);
            return;
        }

        var newCode = printer.getOutput();
        if (ioOptions.stdout) {
            System.out.println(newCode);
        } else {
            try {
                Files.writeString(Path.of(ioOptions.outfile), newCode);
            } catch (IOException e) {
                System.err.println("error: IO error whilst writing output file:");
                e.printStackTrace(System.err);
                System.exit(IO_ERROR);
            }
        }
    }

    private static void printHelp() {
        System.out.println("""
                -- JsonPatcher-Lang Formatter --
                Options:
                  (-i|--input) <file>       Select input file(s)
                  (-o|--output) <file>      Select output file(s)
                  (-O|--stdout)             Write to stdout instead of a file
                  (-c|--max-columns) <uint> Sets the maximum column limit for the formatter (default: 80)
                  (-g|--ignore-errors)      Makes the formatter ignore errors, outputting invalid code
                  (-l|--indent) <string>    Sets the indentation used by the formatter (default: 4 spaces)
                  (-h|--help)               Print this message
                """);
    }
}

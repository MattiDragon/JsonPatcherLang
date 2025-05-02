package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantValue;
import dev.mattidragon.jsonpatcher.lang.analysis.poscheck.PosChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysisDiagnostics;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "ast",
        description = "Prints the abstract syntax tree of JsonPatcher source code",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class AstCommand implements Callable<Integer> {
    @Option(names = {"-d", "--diagnostics"}, defaultValue = "ERRORS",
            description = "The level of diagnostics to output while parsing")
    public DiagnosticLevel diagnosticLevel = DiagnosticLevel.ERRORS;

    @Option(names = {"-a", "--analyser"},
            description = {
                    "Optional additional analysers to run on the AST",
                    "Available options: ${COMPLETION-CANDIDATES}",
                    "Default: ${DEFAULT-VALUE}"
            })
    public Set<AnalysisType> analysers = Set.of(AnalysisType.CONSTANT, AnalysisType.POS_CHECK);

    @Option(names = "--all-metadata",
            defaultValue = "false",
            description = "Prints all available metadata for all AST nodes")
    public boolean allMetadata = false;

    @Parameters(arity = "1..",
            description = "The source file(s) to parse")
    public List<Path> files = List.of();

    @Override
    public Integer call() throws IOException {
        for (var file : files) {
            System.out.println("Processing " + file);
            System.out.flush();
            var diagnostics = new DiagnosticsBuilder();
            String code;
            try {
                code = Files.readString(file);
            } catch (IOException e) {
                throw new IOException("Failed to read input file " + file, e);
            }
            var lex = Lexer.lex(code, file.toString(), diagnostics);
            var parse = Parser.parse(lex.tokens(), diagnostics);
            analysers.stream()
                    .sorted(Comparator.comparing(AnalysisType::ordinal))
                    .forEach(analysisType -> analysisType.run(parse.program(), parse.treeMetadata(), diagnostics));

            var diagnosticList = switch (diagnosticLevel) {
                case NONE -> List.<Diagnostic>of();
                case ERRORS -> diagnostics.build().errors();
                case WARNINGS -> diagnostics.build().errorsAndWarnings();
                case ALL -> diagnostics.build().all();
            };
            for (var diagnostic : diagnosticList) {
                System.err.println(diagnostic.toDisplay());
            }
            if (!diagnosticList.isEmpty()) {
                System.err.println();
                System.err.flush();
            }

            printMetadata(parse.metadata());
            System.out.println("Program:");
            System.out.print("  ");
            print(parse.program(), parse.treeMetadata(), 1);

            System.out.println();
        }
        return 0;
    }

    private void printMetadata(PatchMetadata metadata) {
        System.out.println("Metadata:");
        for (var entry : metadata.getAll().entrySet()) {
            System.out.print("  @" + entry.getKey() + " = ");
            printMetadataValue(entry.getValue(), 1);
        }
    }

    private void printMetadataValue(MetadataElement value, int indent) {
        switch (value) {
            case MetadataArray(var children) -> {
                System.out.println("Array:");
                for (var child : children) {
                    System.out.print("  ".repeat(indent));
                    System.out.print("- ");
                    printMetadataValue(child, indent + 1);
                }
            }
            case MetadataObject(var children) -> {
                System.out.println("Object:");
                for (var entry : children.entrySet()) {
                    System.out.print("  ".repeat(indent));
                    System.out.print(entry.getKey() + ": ");
                    printMetadataValue(entry.getValue(), indent + 1);
                }
            }
            case MetadataBoolean(var bool) -> System.out.println(bool);
            case MetadataNull() -> System.out.println("null");
            case MetadataNumber(var number) -> System.out.println(number);
            case MetadataString(var string) -> {
                printString(string, indent);
                System.out.println();
            }
        }
    }

    private void print(ProgramNode node, TreeMetadata metadata, int indent) {
        switch (node) {
            case ProgramNode other -> {
                System.out.println(other.getClass().getSimpleName());

                if (allMetadata) {
                    printAllMetadata(other, metadata, indent);
                } else {
                    printImportantMetadata(other, metadata, indent);
                }

                for (var child : other.getChildren()) {
                    System.out.print("  ".repeat(indent));
                    System.out.print("- ");
                    print(child, metadata, indent + 1);
                }
            }
        }
    }

    private void printAllMetadata(ProgramNode node, TreeMetadata metadata, int indent) {
        for (var entry : metadata.getAll(node).entrySet()) {
            System.out.print("  ".repeat(indent));
            System.out.print("#" + entry.getKey().name() + ": ");
            var value = switch (entry.getValue()) {
                case SourceSpan span -> span.format();
                case SourcePos pos -> pos.format();
                case Object other -> other.toString();
            };
            printWrapping(value, indent + 1);
            System.out.println();
        }
    }

    private void printImportantMetadata(ProgramNode node, TreeMetadata metadata, int indent) {
        metadata.get(node, ConstantAnalyser.CONSTANT_VALUE)
                .ifPresent(constantValue -> {
                    System.out.print("  ".repeat(indent));
                    System.out.print("#Constant value: ");
                    printConstantValue(constantValue, indent + 1);
                });
        metadata.get(node, VariableAnalyser.VARIABLE_REFERENCE)
                .ifPresent(variableReference -> {
                    System.out.print("  ".repeat(indent));
                    System.out.print("#Variable reference: ");
                    System.out.println(variableReference.name() + "@" + System.identityHashCode(variableReference));
                });
        metadata.get(node, VariableAnalyser.ROOT_REFERENCE)
                .ifPresent(rootReference -> {
                    System.out.print("  ".repeat(indent));
                    System.out.print("#Root reference: ");
                    System.out.println("@" + System.identityHashCode(rootReference));
                });
    }

    private void printConstantValue(ConstantValue constantValue, int indent) {
        switch (constantValue) {
            case ConstantValue.Boolean.FALSE -> System.out.println("false");
            case ConstantValue.Boolean.TRUE -> System.out.println("true");
            case ConstantValue.Null.NULL -> System.out.println("null");
            case ConstantValue.Number(var number) -> System.out.println(number);
            case ConstantValue.String(var string) -> {
                printString(string, indent);
                System.out.println();
            }
        }
    }

    private static void printString(String string, int indent) {
        System.out.print('"');
        printWrapping(string, indent);
        System.out.print('"');
    }

    private static void printWrapping(String string, int indent) {
        for (var c : string.toCharArray()) {
            if (c == '\r') continue;
            if (c == '\n') {
                System.out.println();
                System.out.print("  ".repeat(indent + 1));
            }
            System.out.print(c);
        }
    }

    public enum DiagnosticLevel {
        NONE,
        ERRORS,
        WARNINGS,
        ALL
    }

    public enum AnalysisType {
        CONSTANT {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
                ConstantAnalyser.analyse(ast, metadata);
            }
        },
        POS_CHECK {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
                PosChecker.analyse(ast, metadata, diagnostics);
            }
        },
        VARIABLE {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
                var variableDiagnostics = new DiagnosticsBuilder();
                VariableAnalyser.analyse(ast, metadata, variableDiagnostics, List.of());
                // Remove unknown variable errors, as we don't have globals available
                variableDiagnostics.build().all()
                        .stream()
                        .filter(diagnostic -> !diagnostic.id().equals(VariableAnalysisDiagnostics.UNKNOWN_VARIABLE))
                        .forEach(diagnostics::addDiagnostic);
            }
        },
        TYPE_CHECK {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
                TypeChecker.typeCheck(ast, metadata, diagnostics);
            }
        };

        abstract void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics);
    }
}

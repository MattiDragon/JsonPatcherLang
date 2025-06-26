package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.impl.PrimitivePropertiesLoader;
import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
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
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
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
import java.util.IdentityHashMap;
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

            var commentAttacher = new CommentAttacher();
            var lex = Lexer.lex(code, file.toString(), diagnostics, commentAttacher);
            var parse = Parser.parse(lex.tokens(), diagnostics);
            analysers.stream()
                    .sorted(Comparator.comparing(AnalysisType::ordinal))
                    .forEach(analysisType -> analysisType.run(parse.program(), parse.treeMetadata(), diagnostics, commentAttacher));

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
            System.out.println("Main AST:");
            System.out.print("- ");
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
        var childTags = new IdentityHashMap<ProgramNode, String>();

        System.out.println(node.getClass().getSimpleName());

        switch (node) {
            case UnaryModificationExpression(var postfix, var target, var operator) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(operator: %s, %s)%n", operator, postfix ? "postfix" : "prefix");
            }
            case ErrorExpression(var diagnostic, var child) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(error: %s)%n", diagnostic.message());
                if (child != null) {
                    childTags.put(child, "inner expression");
                }
            }
            case PropertyAccessExpression(var parent, var name) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(name: %s)%n", name);
            }
            case VariableAccessExpression(var name) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(name: %s)%n", name);
            }
            case IndexExpression(var target, var index) -> {
                childTags.put(target, "target");
                childTags.put(index, "index");
            }
            case ShortedBinaryExpression(var first, var second, var op) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(operator: %s)%n", op);
            }
            case BinaryExpression(var first, var second, var op) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(operator: %s)%n", op);
            }
            case AssignmentExpression(var target, var value, var op) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(operator: %s)%n", op);
                childTags.put(target, "target");
                childTags.put(value, "value");
            }
            case TernaryExpression(var condition, var ifTrue, var ifFalse) -> {
                childTags.put(condition, "condition");
                childTags.put(ifTrue, "if true");
                childTags.put(ifFalse, "if false");
            }
            case UnaryExpression(var input, var op) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(operator: %s)%n", op);
            }
            case IsInstanceExpression(var input, var type) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(type: %s)%n", type);
            }
            case FunctionCallExpression(var function, var args) -> {
                childTags.put(function, "function");
                for (var i = 0; i < args.size(); i++) {
                    childTags.put(args.get(i), "arg" + i);
                }
            }
            case StringExpression(var value) -> {
                System.out.print("  ".repeat(indent));
                System.out.print("(");
                printString(value, indent);
                System.out.printf(")%n");
            }
            case NumberExpression(var value) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(%s)%n", value);
            }
            case BooleanExpression(var value) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(%s)%n", value);
            }
            case FunctionArguments(var arguments, var varargs) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(%s)%n", varargs ? "varargs" : "no varargs");
            }
            case FunctionArgument(var target, var defaultValue) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(target: %s)%n", target);
                defaultValue.ifPresent(value -> childTags.put(value, "default value"));
            }
            case ErrorStatement(var diagnostic, var child) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(error: %s)%n", diagnostic.message());
                if (child != null) {
                    childTags.put(child, "inner statement");
                }
            }
            case ApplyStatement(var root, var action) -> {
                childTags.put(root, "root");
                childTags.put(action, "action");
            }
            case VariableCreationStatement(var name, var initializer, var mutable) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(name: %s, %s)%n", name, mutable ? "mutable" : "immutable");
            }
            case ForLoopStatement(Statement initializer, Expression condition, Statement incrementer, Statement body) -> {
                childTags.put(initializer, "initializer");
                childTags.put(condition, "condition");
                childTags.put(incrementer, "incrementer");
                childTags.put(body, "body");
            }
            case ForEachLoopStatement(Expression iterable, String variableName, Statement body) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(variable: %s)%n", variableName);
                childTags.put(iterable, "iterable");
                childTags.put(body, "body");
            }
            case FunctionDeclarationStatement(String name, FunctionExpression value) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(name: %s)%n", name);
            }
            case ImportStatement(String libraryName, String variableName) -> {
                System.out.print("  ".repeat(indent));
                System.out.printf("(library: %s, variable: %s)%n", libraryName, variableName);
            }
            case IfStatement(Expression condition, Statement action, Statement elseAction) -> {
                childTags.put(condition, "condition");
                childTags.put(action, "if true");
                if (elseAction != null) {
                    childTags.put(elseAction, "if false");
                }
            }
            default -> {}
        }

        if (allMetadata) {
            printAllMetadata(node, metadata, indent);
        } else {
            printImportantMetadata(node, metadata, indent);
        }

        for (var child : node.getChildren()) {
            System.out.print("  ".repeat(indent));
            System.out.print("- ");
            var tag = childTags.get(child);
            if (tag != null) {
                System.out.printf("%s: ", tag);
            }
            print(child, metadata, indent + 1);
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
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher) {
                ConstantAnalyser.analyse(ast, metadata);
            }
        },
        POS_CHECK {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher) {
                PosChecker.analyse(ast, metadata, diagnostics);
            }
        },
        VARIABLE {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher) {
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
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher) {
                TypeChecker.typeCheck(ast, metadata, PrimitivePropertiesLoader.PROPERTIES, diagnostics);
            }
        },
        COMMENT_ATTACHER {
            @Override
            void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher) {
                commentAttacher.process(ast, metadata);
            }
        };

        abstract void run(Program ast, TreeMetadata metadata, DiagnosticsBuilder diagnostics, CommentAttacher commentAttacher);
    }
}

package dev.mattidragon.jsonpatcher.cli.commands;

import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import dev.mattidragon.jsonpatcher.docs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.tree.DocTree;
import dev.mattidragon.jsonpatcher.docs.write.DocWriter;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import org.commonmark.node.Document;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Command(name = "docs",
        description = "Format doc comments into markdown files",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class DocsCommand {
    @Spec
    public CommandLine.Model.CommandSpec spec = CommandLine.Model.CommandSpec.create();

    @Option(names = "--header",
            description = "Selects a file whose content will be placed at the beginning of each output file")
    public @Nullable Path header = null;

    @Option(names = "--format", defaultValue = "MARKDOWN",
            description = {"Selects the output format to be used.", "Options: ${COMPLETION-CANDIDATES}"})
    public OutputFormat outputFormat = OutputFormat.MARKDOWN;

    private int headings = 2;

    @Option(names = "--headings", defaultValue = "2",
            description = "Selects the markdown heading level used for top level entries")
    public void setHeadings(int headings) {
        if (headings < 1 || headings > 4) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Headings must be between 1 and 4");
        }
        this.headings = headings;
    }

    @Command(name = "join", aliases = "j", mixinStandardHelpOptions = true,
            description = "Processes multiple source files into a single output file")
    public int joined(
            @Option(names = {"-o", "--out"}, description = "The file to output to", required = true)
            Path outFile,
            @Parameters(arity = "1..",
                    description = "Source files to process")
            List<Path> inFiles
    ) throws IOException {
        var header = this.header == null ? "" : Files.readString(this.header);

        var diagnostics = new DiagnosticsBuilder();
        var treeMetadata = new TreeMetadata();
        var docCommentHandler = new DocCommentHandler(diagnostics, treeMetadata);

        for (var inFile : inFiles) {
            var code = Files.readString(inFile);
            Lexer.lex(code, inFile.toString(), diagnostics, docCommentHandler);
        }

        var builtDiagnostics = diagnostics.build();
        for (var diagnostic : builtDiagnostics.errorsAndWarnings()) {
            System.err.println(diagnostic.toDisplay());
        }

        writeDocs(header, outFile, docCommentHandler);

        return builtDiagnostics.errors().isEmpty() ? 0 : 2;
    }

    @Command(name = "multiple", aliases = {"multi", "m"}, mixinStandardHelpOptions = true,
            description = "Processes source files into individual output doc files")
    public int multiple(
            @Parameters(arity = "1",
                    description = {"The files to process. In the format inputfile=outputfile. Separate using a semicolon"},
                    split = ";",
                    splitSynopsisLabel = ";")
            Map<Path, Path> files
    ) throws IOException {
        var header = this.header == null ? "" : Files.readString(this.header);

        boolean hasErrors = false;

        for (var entry : files.entrySet()) {
            var inFile = entry.getKey();
            var outFile = entry.getValue();

            var diagnostics = new DiagnosticsBuilder();
            var treeMetadata = new TreeMetadata();
            var docCommentHandler = new DocCommentHandler(diagnostics, treeMetadata);

            var code = Files.readString(inFile);
            Lexer.lex(code, inFile.toString(), diagnostics, docCommentHandler);

            var builtDiagnostics = diagnostics.build();
            for (var diagnostic : builtDiagnostics.errorsAndWarnings()) {
                System.err.println(diagnostic.toDisplay());
            }

            if (!builtDiagnostics.errors().isEmpty()) {
                hasErrors = true;
            }

            writeDocs(header, outFile, docCommentHandler);
        }

        return hasErrors ? 2 : 0;
    }

    @Command(name = "single", aliases = "s", mixinStandardHelpOptions = true,
            description = "Processes a single source file into a single output doc file")
    public int single(
            @Parameters(arity = "1",
                    description = "The file to process")
            Path inFile,
            @Parameters(arity = "1",
                    description = "The file to output to")
            Path outFile
    ) throws IOException {
        var header = this.header == null ? "" : Files.readString(this.header);

        var diagnostics = new DiagnosticsBuilder();
        var treeMetadata = new TreeMetadata();
        var docCommentHandler = new DocCommentHandler(diagnostics, treeMetadata);

        var code = Files.readString(inFile);
        Lexer.lex(code, inFile.toString(), diagnostics, docCommentHandler);

        var builtDiagnostics = diagnostics.build();
        for (var diagnostic : builtDiagnostics.errorsAndWarnings()) {
            System.err.println(diagnostic.toDisplay());
        }

        writeDocs(header, outFile, docCommentHandler);

        return builtDiagnostics.errors().isEmpty() ? 0 : 2;
    }

    private void writeDocs(String header, Path outFile, DocCommentHandler docCommentHandler) throws IOException {
        var tree = new DocTree(docCommentHandler.entries());

        try (var output = Files.newBufferedWriter(outFile)) {
            output.write(header);
            var document = new Document();
            DocWriter.write(document, tree, headings);
            outputFormat.renderer.render(document, output);
        }
    }

    public enum OutputFormat {
        HTML(HtmlRenderer.builder().extensions(DocWriter.EXTENSIONS).build()),
        MARKDOWN(MarkdownRenderer.builder().extensions(DocWriter.EXTENSIONS).build());

        private final Renderer renderer;

        OutputFormat(Renderer renderer) {
            this.renderer = renderer;
        }
    }
}

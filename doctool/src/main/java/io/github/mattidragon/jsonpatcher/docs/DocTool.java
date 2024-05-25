package io.github.mattidragon.jsonpatcher.docs;

import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
import io.github.mattidragon.jsonpatcher.docs.write.DocWriter;
import org.commonmark.node.Document;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DocTool {
    private static final String HELP = """
            --- JsonPatcher DocTool ---
            Used to generate markdown and html from jsonpatch documentation comments.
            
            Options:
             -h, -help --help:
                Prints this message
             -i <files>, -in <files>:
                Select input files (can be multiple at once)
             -o <files>, -out <files>:
                Select output files (can be multiple at once)
             -header <file>:
                Selects a file which content will be placed at the beginning of each output file
             -headings <number>:
                Selects the depth of the first heading in the docs (allowed: 1-4, default: 2)
             -j, -join:
                Combine output into a single file
             -html:
                Output html
             -md, -markdown:
                Output markdown (default)
            
            This tool takes in jsonpatch script files and outputs either markdown or html.
            The scripts must consist of valid tokens, but other syntax is not checked.
            
            When join mode is used only one output file should be specified. All docs will
            then be merged into that file. When join mode isn't used the number of input
            and output files should match exactly.
            """;
    
    public static void main(String[] args) {
        var parsedArgs = parseArgs(args);
        if (parsedArgs == null) {
            System.exit(1);
            return;
        }
        var parser = new DocParser();
        List<String> inputFiles = parsedArgs.inputFiles;
        for (int i = 0; i < inputFiles.size(); i++) {
            var file = inputFiles.get(i);
            try {
                System.out.printf("Parsing %s%n", file);
                parser.parse(Files.readString(Path.of(file)), file);
            } catch (IOException e) {
                System.err.println("Error: Failed to read input file " + file);
                e.printStackTrace(System.err);
                System.exit(1);
                return;
            }
            if (!parsedArgs.join) {
                outputParse(parser, parsedArgs, parsedArgs.outputFiles.get(i));
                parser = new DocParser();
            }
        }
        if (parsedArgs.join) {
            outputParse(parser, parsedArgs, parsedArgs.outputFiles.getFirst());
        }
    }
    
    private static void outputParse(DocParser parser, Args args, String outFile) {
        for (var error : parser.getErrors()) {
            System.err.printf("Warn: Error while parsing docs:%n%s%n", error.getMessage());
        }
        
        System.out.printf("Writing %s%n", outFile);
        System.out.flush();
        
        var writer = new DocWriter(parser.getEntries());
        writer.setHeadingLevel(args.headingSize());
        var node = new Document();
        writer.buildDocument(node);
        
        String header = "";
        if (args.headerFile() != null) {
            try {
                header = Files.readString(Path.of(args.headerFile()));
            } catch (IOException e) {
                System.err.println("Error: Failed to read header file");
                e.printStackTrace(System.err);
                System.exit(1);
                return;
            }
        }
        
        try (var out = Files.newBufferedWriter(Path.of(outFile))) {
            out.append(header);
            args.outMode.getRenderer().render(node, out);
        } catch (IOException e) {
            System.err.println("Error: Failed to write to output file " + outFile);
            e.printStackTrace(System.err);
            System.exit(1);
            return;
        }
    }
    
    private record Args(List<String> outputFiles, List<String> inputFiles, OutMode outMode, boolean join, @Nullable String headerFile, int headingSize) {}
    
    private enum OutMode {
        MARKDOWN, HTML;
        
        private Renderer getRenderer() {
            return switch (this) {
                case MARKDOWN -> MarkdownRenderer.builder().extensions(DocWriter.DEFAULT_EXTENSIONS).build();
                case HTML -> HtmlRenderer.builder().extensions(DocWriter.DEFAULT_EXTENSIONS).build();
            };
        }
    }
    
    private static Args parseArgs(String[] args) {
        if (args.length == 0) printHelp();
        
        List<String> outFiles = new ArrayList<>();
        List<String> inFiles = new ArrayList<>();
        String headerFile = null;
        int headings = 0;
        OutMode outMode = null;
        boolean join = false;
        
        String argName = null;
        for (var arg : args) {
            if (arg.startsWith("-")) {
                switch (arg.substring(1)) {
                    case "h", "help", "-help" -> printHelp();
                    case "i", "in" -> argName = "in";
                    case "o", "out" -> argName = "out";
                    case "header" -> argName = "header";
                    case "headings" -> argName = "headings";
                    case "html" -> {
                        if (outMode != null) {
                            System.err.println("Error: Tried to set multiple output modes");
                            return null;
                        }
                        outMode = OutMode.HTML;
                    }
                    case "md", "markdown" -> {
                        if (outMode != null) {
                            System.err.println("Error: Tried to set multiple output modes");
                            return null;
                        }
                        outMode = OutMode.MARKDOWN;
                    }
                    case "j", "join" -> {
                        if (join) {
                            System.err.println("Error: Tried to set join mode multiple times");
                            return null;
                        }
                        join = true;
                    }
                    default -> {
                        System.err.printf("Error: Unknown argument: '%s'%n", arg);
                        return null;
                    }
                }
                continue;
            }
            switch (argName) {
                case "in" -> inFiles.addAll(List.of(arg.split(File.pathSeparator)));
                case "out" -> outFiles.addAll(List.of(arg.split(File.pathSeparator)));
                case "header" -> {
                    if (headerFile != null) {
                        System.err.println("Error: Tried to set header multiple times");
                        return null;
                    }
                    headerFile = arg;
                }
                case "headings" -> {
                    if (headings != 0) {
                        System.err.println("Error: Tried to set headings multiple times");
                        return null;
                    }
                    try {
                        headings = Integer.parseInt(arg);
                    } catch (NumberFormatException e) {
                        System.err.println("Error: Invalid heading size: " + arg);
                        return null;
                    } 
                    if (headings < 1 || headings > 4) {
                        System.err.println("Error: Heading size must be between 1 and 4 (inclusive)");
                        return null;
                    }
                }
                case null -> {
                    System.err.printf("Error: Unexpected positional argument '%s'%n", arg);
                    return null;
                }
                default -> throw new IllegalStateException("Unexpected value: " + argName);
            }
            argName = null;
        }
        
        if (join && outFiles.size() != 1) {
            System.err.println("Error: Join mode requires exactly one output file to be specified");
            return null;
        }
        
        if (!join && outFiles.size() != inFiles.size()) {
            System.err.println("Error: Input and output file counts don't match");
            return null;
        }
        
        if (outMode == null) {
            outMode = OutMode.MARKDOWN;
        }
        
        if (headings == 0) {
            headings = 2;
        }
        
        return new Args(outFiles, inFiles, outMode, join, headerFile, headings);
    }
    
    private static void printHelp() {
        System.out.println(HELP);
        System.exit(0);
    }
}

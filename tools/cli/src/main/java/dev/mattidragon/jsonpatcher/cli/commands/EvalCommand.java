package dev.mattidragon.jsonpatcher.cli.commands;

import com.google.gson.*;
import dev.mattidragon.jsonpatcher.cli.impl.VersionProvider;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.SuppressingCommentDiagnosticFilter;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ReturnStatement;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.EvaluationEnvironment;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryGroup;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.ProgramData;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "run",
        aliases = {"eval"},
        description = "Evaluates a snippet of jsonpatcher code",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class)
public class EvalCommand implements Callable<Integer> {
    private static final Gson GSON = new Gson();

    @CommandLine.Option(names = {"-i", "--input-file"},
            description = "Path to a json file to use as input")
    public @Nullable Path jsonPath = null;

    @CommandLine.Option(names = {"-o", "--output-file"},
            description = "Path to write the output json to (defaults to stdout)")
    public @Nullable Path outputFile = null;

    @CommandLine.Option(names = {"-e", "--expression"},
            description = "Parse code as an expression instead of a full program")
    public boolean expressionMode = false;

    @CommandLine.ArgGroup(multiplicity = "1")
    public CodeSource codeSource = new CodeSource();

    public static class CodeSource {
        @CommandLine.Option(names = {"-f", "--source-file"},
                description = "Path to a file containing jsonpatcher code to evaluate")
        public @Nullable Path sourceFile = null;

        @CommandLine.Parameters(arity = "1",
                description = "Jsonpatcher code to evaluate (if --source-file is not set)")
        public @Nullable String code = null;
    }

    @Override
    public Integer call() throws IOException {
        // Read files
        var json = getJson(jsonPath);
        var code = getCode(codeSource);

        // Parse code
        var diagnosticFilter = new SuppressingCommentDiagnosticFilter();
        var diagnostics = new DiagnosticsBuilder();
        var tokens = Lexer.lex(code, "input", diagnostics, diagnosticFilter).tokens();
        Program program;
        var metadata = new TreeMetadata();
        if (expressionMode) {
            var expression = Parser.parseExpression(tokens, diagnostics, metadata);
            program = new Program(List.of(new ReturnStatement(Optional.of(expression))));
        } else {
            var parse = Parser.parse(tokens, diagnostics, metadata);
            program = parse.program();
        }

        var exitCode = 0;
        for (var diagnostic : diagnostics.build(diagnosticFilter).all()) {
            System.err.println(diagnostic.toDisplay());
            if (diagnostic.kind() == Diagnostic.Kind.ERROR || diagnostic.kind() == Diagnostic.Kind.INTERNAL_ERROR) {
                exitCode = 1;
            }
        }
        if (exitCode != 0) {
            return exitCode;
        }

        // Setup env
        var environment = new EvaluationEnvironment(CompilerOptions.DEFAULT);
        environment.enableLogging(value -> {
            var s = value.toString();
            for (var string : s.split("\n")) {
                System.err.printf("[log] %s%n", string);
            }
        });
        environment.bootstrap();

        // Install code into env
        var programData = ProgramData.builder(program, metadata)
                .allowLibraryGroup(LibraryGroup.REFLECTION)
                .scriptName("eval_source")
                .className("eval_code")
                .build();
        var addedProgram = environment.addProgram(programData);

        // Run code
        var object = convertFromJsonObject(json);
        var returnValue = addedProgram.run(object);
        if (returnValue != Value.NullValue.NULL || expressionMode) {
            System.err.printf("Program returned: %s%n", returnValue);
        }

        // Attempt to sync streams for nicer console usage
        System.err.flush();
        System.out.flush();

        var outputJson = convertToJson(object);
        try (var writer = getWriter(outputFile)) {
            GSON.toJson(outputJson, writer);
        }

        return 0;
    }

    private static Value.ObjectValue convertFromJsonObject(JsonObject json) {
        var map = new LinkedHashMap<String, Value>();
        for (var entry : json.entrySet()) {
            map.put(entry.getKey(), convertFromJson(entry.getValue()));
        }
        return new Value.ObjectValue(map);
    }

    private static Value convertFromJson(JsonElement element) {
        return switch (element) {
            case JsonObject object -> convertFromJsonObject(object);
            case JsonArray array -> new Value.ArrayValue(array.asList()
                    .stream()
                    .map(EvalCommand::convertFromJson)
                    .toList());
            case JsonNull jsonNull -> Value.NullValue.NULL;
            case JsonPrimitive primitive when primitive.isBoolean() -> Value.BooleanValue.of(primitive.getAsBoolean());
            case JsonPrimitive primitive when primitive.isNumber() -> new Value.NumberValue(primitive.getAsDouble());
            case JsonPrimitive primitive when primitive.isString() -> new Value.StringValue(primitive.getAsString());
            default -> throw new IllegalStateException("Unknown JSON element: " + element);
        };
    }

    private static JsonElement convertToJson(Value value) {
        return switch (value) {
            case Value.ObjectValue objectValue -> {
                var obj = new JsonObject();
                for (var entry : objectValue.value().entrySet()) {
                    obj.add(entry.getKey(), convertToJson(entry.getValue()));
                }
                yield obj;
            }
            case Value.ArrayValue arrayValue -> {
                var arr = new JsonArray();
                for (var item : arrayValue.value()) {
                    arr.add(convertToJson(item));
                }
                yield arr;
            }
            case Value.StringValue stringValue -> new JsonPrimitive(stringValue.value());
            case Value.NumberValue numberValue -> new JsonPrimitive(numberValue.value());
            case Value.BooleanValue booleanValue -> new JsonPrimitive(booleanValue.value());
            case Value.NullValue ignored -> JsonNull.INSTANCE;
            default -> throw new IllegalStateException("Unsupported value in json: " + value);
        };
    }

    private static JsonObject getJson(@Nullable Path jsonPath) throws IOException {
        if (jsonPath == null) {
            return new JsonObject();
        }

        try (var reader = getReader(jsonPath)) {
            return GSON.fromJson(reader, JsonObject.class);
        }
    }

    private static String getCode(CodeSource codeSource) throws IOException {
        if (codeSource.sourceFile != null) {
            try (var reader = getReader(codeSource.sourceFile)) {
                var builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        }
        if (codeSource.code != null) {
            return codeSource.code;
        }
        throw new IllegalStateException("No code specified");
    }

    private static BufferedReader getReader(Path path) throws IOException {
        if ("-".equals(path.toString())) {
            return new BufferedReader(new InputStreamReader(System.in));
        }

        return Files.newBufferedReader(path);
    }

    private static BufferedWriter getWriter(@Nullable Path outputFile) throws IOException {
        if (outputFile == null) {
            return new BufferedWriter(new OutputStreamWriter(System.out));
        }

        return Files.newBufferedWriter(outputFile);
    }
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.PropertyHolder;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.LibraryBuilder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EvaluationEnvironment {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String[] STD_LIBS = {"arrays", "debug", "functions", "math", "objects", "strings"};

    private final Map<String, Value> globals = new HashMap<>();
    private final Map<String, Consumer<Value.ObjectValue>> libraries = new HashMap<>();
    private final PropertyHolder propertyHolder = new PropertyHolder();
    private final ScriptClassLoader classLoader = new ScriptClassLoader();
    private final CompilerOptions compilerOptions;
    private final LangConfig config;

    @Nullable
    private String dumpPath = null;

    public EvaluationEnvironment(CompilerOptions compilerOptions) {
        this.compilerOptions = compilerOptions;
        this.config = compilerOptions.langConfig();
    }

    public void bootstrap() {
        var diagnosticsBuilder = new DiagnosticsBuilder();

        for (var name : STD_LIBS) {
            globals.put(name, new Value.ObjectValue());
        }

        for (var name : STD_LIBS) {
            var fileName = "bytecode-runtime-files/stdlib/" + name + ".jsonpatch";

            String content;
            try (var stream = getClass().getClassLoader().getResourceAsStream(fileName)) {
                if (stream == null) throw new IllegalStateException("Cannot find stdlib " + name);
                content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read stdlib " + name, e);
            }

            var lex = Lexer.lex(content, "stdlib/" + name + ".jsonpatch", diagnosticsBuilder);
            var parse = Parser.parse(lex.tokens(), diagnosticsBuilder);

            var instance = classLoader.addScript(parse.program(), parse.treeMetadata(), compilerOptions,
                    "stdlib/" + name + ".jsonpatch", "jsonpatcher_generated/stdlib/" + name);

            instance.run((Value.ObjectValue) globals.get(name), globals);
        }

        var diagnostics = diagnosticsBuilder.build();
        var errors = diagnostics.errorsAndWarnings();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Failed to bootstrap evaluation environment:\n"
                + errors.stream().map(Diagnostic::toDisplay).collect(Collectors.joining("\n\n")));
        }
    }
    
    public void addLibrary(String name, Value.ObjectValue value) {
        libraries.put(name, root -> root.value().putAll(value.value()));
    }

    public void enableDumping(String path) {
        dumpPath = path;
    }
    
    public AddedProgram addProgram(String code, String scriptName, String className) {
        var diagnosticsBuilder = new DiagnosticsBuilder();

        var lex = Lexer.lex(code, scriptName, diagnosticsBuilder);
        var parse = Parser.parse(lex.tokens(), diagnosticsBuilder);

        var diagnostics = diagnosticsBuilder.build();
        var errors = diagnostics.errorsAndWarnings();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Failed to parse script %s:\n%s".formatted(
                    scriptName,
                    errors.stream().map(Diagnostic::toDisplay).collect(Collectors.joining("\n\n"))
            ));
        }

        return addProgram(parse.program(), parse.treeMetadata(), scriptName, className);
    }

    public AddedProgram addProgram(Program program, TreeMetadata metadata, String scriptName, String className) {
        var instance = classLoader.addScript(program, metadata, compilerOptions,
                scriptName, className);

        return new AddedProgram(instance);
    }

    private void configureCompiler(PreparationContextBuilder builder) {
        builder.declareVariables(STD_LIBS);
    }

    private void locateLibrary(String name, Value.ObjectValue value, PlatformContext context) {
        if (name.equals("@internals")) {
            new LibraryBuilder(BytecodeInternalsLibrary.class, new BytecodeInternalsLibrary(propertyHolder))
                    .build(value);
            return;
        }
        if (libraries.containsKey(name)) {
            libraries.get(name).accept(value);
            return;
        }
        throw new NoSuchElementException("Cannot find library " + name);
    }
    
    public final class AddedProgram {
        private final GeneratedProgram program;

        private AddedProgram(GeneratedProgram program) {
            this.program = program;
        }

        public Value run(Value.ObjectValue root) {
            return program.run(root, globals);
        }
    }
    
    private class ScriptClassLoader extends ClassLoader {
        public GeneratedProgram addScript(Program program, TreeMetadata metadata, CompilerOptions compilerOptions, String scriptName, String className) {
            byte[] bytes;
            try {
                bytes = ScriptCompiler.compile(program, metadata, compilerOptions, EvaluationEnvironment.this::configureCompiler, scriptName, className);
            } catch (CompilationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to compile script " + scriptName, e);
            }

            if (dumpPath != null) {
                try {
                    var path = Path.of(dumpPath, className + ".class");
                    Files.createDirectories(path.getParent());
                    Files.write(path, bytes);
                } catch (IOException e) {
                    throw new IllegalStateException("failed to dump", e);
                }
            }

            var clazz = defineClass(null, bytes, 0, bytes.length);
            try {
                var constructor = LOOKUP.findConstructor(clazz, MethodType.methodType(void.class, EvaluationContext.class));
                var instance = constructor.invoke(new EvaluationContext(config, propertyHolder, EvaluationEnvironment.this::locateLibrary));
                return (GeneratedProgram) instance;
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to instantiate script", e);
            }
        }

    }
}

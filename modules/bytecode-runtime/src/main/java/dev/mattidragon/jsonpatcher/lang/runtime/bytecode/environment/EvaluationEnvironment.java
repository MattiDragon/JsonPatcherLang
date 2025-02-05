package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.environment;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.Stdlib;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.PropertyHolder;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.LibraryBuilder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class EvaluationEnvironment {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Map<String, Value> globals = new HashMap<>();
    private final Map<String, Library> libraries = new HashMap<>();
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
        addLibrary(new Library(
                LibraryGroup.INTERNALS,
                "@internals",
                () -> {
                    var obj = new Value.ObjectValue();
                    new LibraryBuilder(BytecodeInternalsLibrary.class, new BytecodeInternalsLibrary(propertyHolder))
                            .build(obj);
                    return obj;
                }
        ));

        var diagnosticsBuilder = new DiagnosticsBuilder();

        // Prepare objects beforehand to prevent issues when libs use each other
        for (var name : Stdlib.LIBRARY_NAMES) {
            globals.put(name, new Value.ObjectValue());
        }

        Stdlib.LIBRARY_CONTENTS.forEach((name, content) -> {
            var lex = Lexer.lex(content, "stdlib/" + name + ".jsonpatch", diagnosticsBuilder);
            var parse = Parser.parse(lex.tokens(), diagnosticsBuilder);

            var instance = classLoader.addScript(
                    parse.program(),
                    parse.treeMetadata(),
                    compilerOptions,
                    "stdlib/" + name + ".jsonpatch", "jsonpatcher_generated/stdlib/" + name,
                    List.of(LibraryGroup.DEFAULT, LibraryGroup.INTERNALS)
            );

            instance.run((Value.ObjectValue) globals.get(name), globals);
        });

        // Freeze stdlib after init
        for (var name : Stdlib.LIBRARY_NAMES) {
            globals.put(name, new Value.ObjectValue(((Value.ObjectValue) globals.get(name)).value(), true));
        }

        var diagnostics = diagnosticsBuilder.build();
        var errors = diagnostics.errorsAndWarnings();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Failed to bootstrap evaluation environment:\n"
                + errors.stream().map(Diagnostic::toDisplay).collect(Collectors.joining("\n\n")));
        }
    }
    
    public void addLibrary(Library library) {
        libraries.put(library.name(), library);
    }

    public void enableDumping(String path) {
        dumpPath = path;
    }
    
    public AddedProgram addProgram(String code, String scriptName, String className, Collection<LibraryGroup> allowedLibraries) {
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

        return addProgram(parse.program(), parse.treeMetadata(), scriptName, className, allowedLibraries);
    }

    public AddedProgram addProgram(Program program, TreeMetadata metadata, String scriptName, String className, Collection<LibraryGroup> allowedLibraries) {
        var instance = classLoader.addScript(program, metadata, compilerOptions, scriptName, className, allowedLibraries);
        return new AddedProgram(instance);
    }

    private void configureCompiler(PreparationContextBuilder builder) {
        builder.declareVariables(Stdlib.LIBRARY_NAMES);
    }

    private Value.ObjectValue locateLibrary(String name, Collection<LibraryGroup> libraryGroups) {
        if (!libraries.containsKey(name)) {
            throw new NoSuchElementException("Cannot find library " + name);
        }
        var lib = libraries.get(name);
        if (!libraryGroups.contains(lib.group())) {
            throw new IllegalStateException("Library %s is not available to the current program. It is in group %s and the current program has access to the groups %s"
                    .formatted(name, lib.group().name(), libraryGroups.stream().map(LibraryGroup::name).collect(Collectors.joining(", "))));
        }
        return lib.contents();
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
        public GeneratedProgram addScript(Program program, TreeMetadata metadata, CompilerOptions compilerOptions, String scriptName, String className, Collection<LibraryGroup> allowedLibraries) {
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
                LibraryLookup libraryLocator = (name) -> EvaluationEnvironment.this.locateLibrary(name, allowedLibraries);
                var instance = constructor.invoke(new EvaluationContext(config, propertyHolder, libraryLocator));
                return (GeneratedProgram) instance;
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to instantiate script", e);
            }
        }

    }
}

package dev.mattidragon.jsonpatcher.lang.runtime.environment;

import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;
import dev.mattidragon.jsonpatcher.lang.stdlib.Stdlib;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.BytecodeInternalsLibrary;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.builder.LibraryBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.ReflectionInternalsLibrary;
import dev.mattidragon.jsonpatcher.lang.runtime.util.PropertyHolder;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class EvaluationEnvironment {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final Map<String, Value> globals = new HashMap<>();
    private final Map<String, Library> libraries = Collections.synchronizedMap(new HashMap<>());
    private final PropertyHolder propertyHolder = new PropertyHolder();
    private final ScriptClassLoader classLoader = new ScriptClassLoader();
    private final CompilerOptions compilerOptions;

    @Nullable
    private Path dumpPath = null;
    private Consumer<Value> logConsumer = v -> {};

    public EvaluationEnvironment(CompilerOptions compilerOptions) {
        this.compilerOptions = compilerOptions;
    }

    public void bootstrap() {
        addLibrary(new Library(
                LibraryGroup.INTERNALS,
                "@internals",
                () -> {
                    var obj = new Value.ObjectValue();
                    new LibraryBuilder(BytecodeInternalsLibrary.class, new BytecodeInternalsLibrary(v -> this.logConsumer.accept(v), propertyHolder))
                            .build(obj);
                    return obj;
                }
        ));
        addLibrary(new Library(
                LibraryGroup.INTERNALS,
                "@internals/reflection",
                () -> {
                    var obj = new Value.ObjectValue();
                    new LibraryBuilder(ReflectionInternalsLibrary.class, new ReflectionInternalsLibrary()).build(obj);
                    return obj;
                }
        ));

        var diagnosticsBuilder = new DiagnosticsBuilder();

        // Prepare objects beforehand to prevent issues when libs use each other
        for (var name : Stdlib.GLOBAL_LIBRARY_NAMES) {
            globals.put(name, new Value.ObjectValue());
        }

        for (var name : Stdlib.GLOBAL_LIBRARY_NAMES) {
            var content = Stdlib.LIBRARY_CONTENTS.get(name);
            var lex = Lexer.lex(content, "stdlib/" + name + ".jsonpatch", diagnosticsBuilder);
            var parse = Parser.parse(lex.tokens(), diagnosticsBuilder);

            checkStdlibDiagnostics(name, diagnosticsBuilder);

            var instance = classLoader.addScript(
                    ProgramData.builder(parse)
                            .scriptName("stdlib/" + name + ".jsonpatch")
                            .className("jsonpatcher_builtin/global/" + name)
                            .allowLibraryGroup(LibraryGroup.INTERNALS)
                            .build(),
                    compilerOptions
            );

            instance.run((Value.ObjectValue) globals.get(name), globals);
        }

        // Freeze stdlib after init
        for (var name : Stdlib.GLOBAL_LIBRARY_NAMES) {
            globals.put(name, new Value.ObjectValue(((Value.ObjectValue) globals.get(name)).value(), true));
        }

        // Prepare non-global stdlibs
        for (var name : Stdlib.MISC_LIBRARY_NAMES) {
            var content = Stdlib.LIBRARY_CONTENTS.get(name);
            var lex = Lexer.lex(content, "stdlib/" + name + ".jsonpatch", diagnosticsBuilder);
            var parse = Parser.parse(lex.tokens(), diagnosticsBuilder);

            checkStdlibDiagnostics(name, diagnosticsBuilder);

            var instance = classLoader.addScript(
                    ProgramData.builder(parse)
                            .scriptName("stdlib/" + name + ".jsonpatch")
                            .className("jsonpatcher_builtin/libraries/" + name)
                            .allowLibraryGroup(LibraryGroup.INTERNALS)
                            .build(),
                    compilerOptions
            );

            var libraryGroup = switch (parse.metadata().has("libgroup") ? parse.metadata().getString("libgroup") : null) {
                case "reflection" -> LibraryGroup.REFLECTION;
                case "default" -> LibraryGroup.DEFAULT;
                case null -> LibraryGroup.DEFAULT;
                case String group ->
                        throw new IllegalArgumentException("Unsupported library group in stdlib: " + group);
            };

            var object = new Value.ObjectValue();
            instance.run(object, globals);
            var frozenObject = new Value.ObjectValue(object.value(), true);
            addLibrary(new Library(
                    libraryGroup,
                    name,
                    () -> frozenObject
            ));
        }

        var diagnostics = diagnosticsBuilder.build();
        var errors = diagnostics.errorsAndWarnings();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Failed to bootstrap evaluation environment:\n"
                                            + errors.stream().map(Diagnostic::toDisplay).collect(Collectors.joining("\n\n")));
        }
    }

    private void checkStdlibDiagnostics(String libName, DiagnosticsBuilder builder) {
        var errors = builder.build().errors();
        if (errors.isEmpty()) return;
        var msg = new StringBuilder("Errors while compiling stdlib '" + libName + "'");
        for (var error : errors) {
            msg.append("\n").append(error.toDisplay());
        }
        throw new IllegalStateException(msg.toString());
    }

    public void addLibrary(Library library) {
        libraries.put(library.name(), library);
    }

    public void enableDumping(String path) {
        enableDumping(Path.of(path));
    }

    public void enableDumping(Path path) {
        dumpPath = path;
    }

    public void enableLogging(Consumer<Value> consumer) {
        logConsumer = consumer;
    }

    public AddedProgram addProgram(ProgramData data) {
        return new AddedProgram(classLoader.addScript(data, compilerOptions));
    }

    private Set<String> getNamesGlobal() {
        return Set.of(Stdlib.GLOBAL_LIBRARY_NAMES);
    }

    private Value.ObjectValue locateLibrary(String name, Collection<LibraryGroup> libraryGroups) {
        if (!libraries.containsKey(name)) {
            throw new NoSuchElementException("Cannot find library " + name);
        }
        var lib = libraries.get(name);
        if (!libraryGroups.contains(lib.group())) {
            throw new IllegalStateException("Library %s is not available to the current program. It is in group %s and the current program has access to the groups [%s]"
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
        private final List<String> usedNames = new ArrayList<>();

        protected ScriptClassLoader() {
            super(ScriptClassLoader.class.getClassLoader());
        }

        public GeneratedProgram addScript(ProgramData data, CompilerOptions compilerOptions) {
            var className = data.className();
            while (usedNames.contains(className)) {
                className += "_" + usedNames.size();
            }
            usedNames.add(className);

            byte[] bytes;
            try {
                // TODO: propagate diagnostics
                bytes = ScriptCompiler.compile(
                        data.program(),
                        data.metadata(),
                        compilerOptions,
                        getNamesGlobal(),
                        data.scriptName(),
                        className,
                        new DiagnosticsBuilder());
            } catch (CompilationException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to compile script " + data.scriptName(), e);
            }

            if (dumpPath != null) {
                try {
                    var path = dumpPath.resolve(className + ".class");
                    Files.createDirectories(path.getParent());
                    Files.write(path, bytes);
                } catch (IOException e) {
                    throw new IllegalStateException("failed to dump", e);
                }
            }

            var clazz = defineClass(null, bytes, 0, bytes.length);
            try {
                var constructor = LOOKUP.findConstructor(clazz, MethodType.methodType(void.class, EvaluationContext.class));
                LibraryLookup libraryLocator = (name) -> EvaluationEnvironment.this.locateLibrary(name, data.allowedLibraries());
                var instance = constructor.invoke(new EvaluationContext(propertyHolder, libraryLocator));
                return (GeneratedProgram) instance;
            } catch (Throwable e) {
                throw new IllegalStateException("Failed to instantiate script", e);
            }
        }
    }
}

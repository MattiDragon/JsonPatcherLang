package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.DummyPropertyLookup;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BytecodeTestRunner implements TestRunner {
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();
    private static final TestClassLoader CLASS_LOADER = new TestClassLoader();
    private static final LangConfig LANG_CONFIG = new LangConfig(LangConfig.StackTraceMode.JAVA);
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Path DUMP_PATH = Path.of("build", "tmp", "test-class-dump");
    
    static {
        try {
            if (Files.exists(DUMP_PATH)) {
                Files.walkFileTree(DUMP_PATH, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            System.out.println("Dumping test classes to " + DUMP_PATH.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to clean dump", e);
        }
    }

    @Override
    public String name() {
        return "bytecode compiler";
    }

    @Override
    public void executeCode(Program program, TreeMetadata metadata, Map<String, Value.ObjectValue> libraries) {
        var className = "dev/mattidragon/jsonpatcher/lang/test/TestScript" + CLASS_COUNTER.getAndIncrement();
        var bytes = ScriptCompiler.compile(program,
                metadata,
                CompilerOptions.DEFAULT,
                PreparationContextBuilder::declareStdlib,
                "test script",
                className);
        
        try {
            Files.createDirectories(DUMP_PATH);
            Files.write(DUMP_PATH.resolve(className.substring(className.lastIndexOf("/") + 1) + ".class"), bytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to dump compiled class", e);
        }

        // Run asm verifier to get additional info in case we create invalid class files
        CheckClassAdapter.verify(new ClassReader(bytes), false, new PrintWriter(System.out));
        // Flush stdout and stderr to hopefully avoid them mixing together too much
        System.out.flush();
        System.err.flush();

        var scriptClass = CLASS_LOADER.defineClass(bytes);
        try {
            LOOKUP.ensureInitialized(scriptClass);
            var constructor = LOOKUP.findConstructor(scriptClass, MethodType.methodType(void.class, EvaluationContext.class));
            var instance = (GeneratedProgram) constructor.invoke(new EvaluationContext(LANG_CONFIG, DummyPropertyLookup.INSTANCE, (name, object, context) -> object.value().putAll(libraries.get(name).value())));
            var globals = new HashMap<String, Value>();
            Libraries.BUILTIN.forEach((name, supplier) -> globals.put(name, supplier.get()));
            instance.run(new Value.ObjectValue(), globals);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run test script", e);
        }
    }

    private static class TestClassLoader extends ClassLoader {
        public Class<?> defineClass(byte[] bytes) {
            return defineClass(null, bytes, 0, bytes.length);
        }
    }
}

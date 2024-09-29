package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Compiler {
    public static void main(String[] args) throws IOException {
        var code = """
                val object = {a: () -> object};
                return object;
                """;
        var lex = Lexer.lex(TestUtils.CONFIG, code, "test_program");
        if (!lex.errors().isEmpty()) {
            var e = new IllegalStateException("Failed to lex");
            for (var error : lex.errors()) {
                e.addSuppressed(error);
            }
            throw e;
        }
        var parse = Parser.parse(TestUtils.CONFIG, lex.tokens());
        if (!parse.errors().isEmpty()) {
            var e = new IllegalStateException("Failed to parse");
            for (var error : parse.errors()) {
                e.addSuppressed(error);
            }
            throw e;
        }

        var bytes = ScriptCompiler.compile(parse.program(), parse.treeMetadata(), new LangConfig(LangConfig.StackTraceMode.JAVA), builder -> builder.declareVariables("my_global", "debug", "objects", "strings"));

        var reader = new ClassReader(bytes);
        CheckClassAdapter.verify(reader, false, new PrintWriter(System.out));

        var className = reader.getClassName();
        Files.write(Path.of("run", className.substring(className.lastIndexOf('/') + 1) + ".class"), bytes);

        try {
//            var definedLookup = GeneratedProgram.PACKAGE_ACCESS.defineHiddenClass(bytes, false);
//            var constructor = definedLookup.findConstructor(definedLookup.lookupClass(), MethodType.methodType(void.class, EvaluationContext.class));
            var clazz = GeneratedProgram.PACKAGE_ACCESS.defineClass(bytes);
            var constructor = GeneratedProgram.PACKAGE_ACCESS.findConstructor(clazz, MethodType.methodType(void.class, EvaluationContext.class));
            var instance = (GeneratedProgram) constructor.invoke(new EvaluationContext(new LangConfig(LangConfig.StackTraceMode.JAVA), (libraryName, libraryObject, context) -> {
                libraryObject.setProperty("test", new Value.NumberValue(10), context);
                libraryObject.setProperty("name", new Value.StringValue(libraryName), context);
            }));
            System.out.println("Result: " + instance.run(new Value.ObjectValue(), Map.of(
                    "my_global", new Value.NumberValue(5), 
                    "debug", new Value.ObjectValue(), 
                    "objects", new Value.ObjectValue(), 
                    "strings", new Value.ObjectValue())));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run", e);
        }
    }
}

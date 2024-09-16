package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.ScriptCompiler;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated.GeneratedProgram;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

public class Compiler {
    public static void main(String[] args) throws IOException {
        var code = """
                val a = 10;
                function myFunction(b, $) {
                    return my_global + a;
                }
                return myFunction;
                """;
        var lex = Lexer.lex(TestUtils.CONFIG, code, "test_program");
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, lex.errors(), "Failed to lex");
        var parse = Parser.parse(TestUtils.CONFIG, lex.tokens());
        Assertions.assertIterableEquals(Collections.EMPTY_LIST, parse.errors(), "Failed to parse");

        var bytes = ScriptCompiler.compile(parse.program(), parse.treeMetadata(), new LangConfig(LangConfig.StackTraceMode.JAVA), builder -> builder.declareVariable("my_global"));

        var reader = new ClassReader(bytes);
        CheckClassAdapter.verify(reader, false, new PrintWriter(System.err));

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
            System.out.println("Result: " + instance.run(new Value.ObjectValue(), Map.of("my_global", new Value.NumberValue(5))));
        } catch (Throwable e) {
            throw new RuntimeException("Failed to run", e);
        }
    }
}

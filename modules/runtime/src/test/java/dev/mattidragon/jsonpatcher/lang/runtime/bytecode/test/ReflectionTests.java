package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.Library;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryGroup;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.ProgramData;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public class ReflectionTests {
    private final BytecodeTestRunner runner = new BytecodeTestRunner(CompilerOptions.DEFAULT);

    public ReflectionTests() {
        var driverCode = """
                import "reflection";
                
                val Assertions = reflection.findClass("org.junit.jupiter.api.Assertions");
                val TestUtils = reflection.findClass("dev.mattidragon.jsonpatcher.lang.test.TestUtils")
                
                # (expected, actual) -> null
                $assertEquals = TestUtils.assertEquals;
                """;

        var diagnostics = new DiagnosticsBuilder();
        var lex = Lexer.lex(driverCode, "assertions", diagnostics);
        var parse = Parser.parse(lex.tokens(), diagnostics);

        var program = runner.environment().addProgram(ProgramData.builder(parse)
                .scriptName("assertions")
                .className("jsonpatcher_generated/assertions")
                .allowLibraryGroup(LibraryGroup.REFLECTION)
                .build()
        );
        var libObject = new Value.ObjectValue();
        program.run(libObject);
        runner.environment().addLibrary(new Library(LibraryGroup.DEFAULT, "assertions", () -> libObject));
    }

    @Test
    public void testMethodLinking() {
        var code = """
                import "assertions";
                import "reflection";
                
                var TestJavaMethods = reflection.findClass("dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test.TestJavaMethods");
                
                TestJavaMethods.staticVoidMethod(10, "hello");
                assertions.assertEquals(TestJavaMethods.INFO, "Lorem ipsum");
                
                var instance = TestJavaMethods.'<init>()V'();
                instance.instanceMethod(10, null);
                instance.instanceMethod("a", "b");
                assertions.assertEquals(2, instance.'val');
                
                var secondInstance = TestJavaMethods();
                secondInstance.instanceMethod(10, null);
                assertions.assertEquals(1, secondInstance.'val');
                """;

        TestUtils.runCode(runner, code);
    }
}

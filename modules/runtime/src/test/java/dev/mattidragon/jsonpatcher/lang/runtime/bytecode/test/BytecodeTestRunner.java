package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.EvaluationEnvironment;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.Library;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryGroup;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.ProgramData;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import dev.mattidragon.jsonpatcher.lang.test.TestNameProvider;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BytecodeTestRunner implements TestRunner {
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    private final EvaluationEnvironment environment;
    private final TestNameProvider testNameProvider;

    public BytecodeTestRunner(CompilerOptions compilerOptions, TestNameProvider testNameProvider) {
        environment = new EvaluationEnvironment(compilerOptions);
        environment.enableDumping("build/tmp/test-class-dump/env");
        environment.enableLogging(v -> System.out.println("Debug from test: " + v));
        environment.bootstrap();
        this.testNameProvider = testNameProvider;
    }

    @Override
    public String name() {
        return "bytecode compiler";
    }

    public EvaluationEnvironment environment() {
        return environment;
    }

    @Override
    public void executeCode(Program program, TreeMetadata metadata, Map<String, Value.ObjectValue> libraries) {
        var cleanedName = testNameProvider.getTestName()
                .replaceFirst("\\(.*$", "")
                .replaceAll("[^a-zA-Z0-9_]", "_");

        libraries.forEach((name, contents) -> environment.addLibrary(new Library(LibraryGroup.DEFAULT, name, () -> contents)));
        var added = environment.addProgram(ProgramData.builder(program, metadata)
                .scriptName("test_script")
                .className("jsonpatcher_generated/test" + CLASS_COUNTER.getAndIncrement() + "_" + cleanedName)
                .allowLibraryGroup(LibraryGroup.REFLECTION)
                .build());
        added.run(new Value.ObjectValue());
    }
}

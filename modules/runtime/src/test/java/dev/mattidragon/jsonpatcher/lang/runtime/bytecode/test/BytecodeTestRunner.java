package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.EvaluationEnvironment;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.Library;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryGroup;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.ProgramData;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BytecodeTestRunner implements TestRunner {
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    private final EvaluationEnvironment environment;

    public BytecodeTestRunner(CompilerOptions compilerOptions) {
        environment = new EvaluationEnvironment(compilerOptions);
        environment.enableDumping("build/tmp/test-class-dump/env");
        environment.bootstrap();
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
        libraries.forEach((name, contents) -> environment.addLibrary(new Library(LibraryGroup.DEFAULT, name, () -> contents)));
        var added = environment.addProgram(ProgramData.builder(program, metadata)
                .scriptName("test_script")
                .className("jsonpatcher_generated/test" + CLASS_COUNTER.getAndIncrement())
                .allowLibraryGroup(LibraryGroup.REFLECTION)
                .build());
        added.run(new Value.ObjectValue());
    }
}

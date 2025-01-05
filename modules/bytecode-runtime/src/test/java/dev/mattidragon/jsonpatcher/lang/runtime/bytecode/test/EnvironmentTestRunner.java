package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.EvaluationEnvironment;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class EnvironmentTestRunner implements TestRunner {
    private static final AtomicInteger CLASS_COUNTER = new AtomicInteger();

    private final EvaluationEnvironment environment;

    public EnvironmentTestRunner(CompilerOptions compilerOptions) {
        environment = new EvaluationEnvironment(compilerOptions);
        environment.enableDumping("build/tmp/test-class-dump/env");
        environment.bootstrap();
    }

    @Override
    public String name() {
        return "bytecode compiler";
    }

    @Override
    public void executeCode(Program program, TreeMetadata metadata, Map<String, Value.ObjectValue> libraries) {
        libraries.forEach(environment::addLibrary);
        var added = environment.addProgram(program, metadata, "test_script", "jsonpatcher_generated/test" + CLASS_COUNTER.getAndIncrement());
        added.run(new Value.ObjectValue());
    }
}

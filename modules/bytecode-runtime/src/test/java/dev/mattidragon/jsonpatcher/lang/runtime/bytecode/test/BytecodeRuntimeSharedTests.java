package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;
import dev.mattidragon.jsonpatcher.lang.runtime.legacy.EvaluationException;
import dev.mattidragon.jsonpatcher.lang.test.AllSharedTests;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

public class BytecodeRuntimeSharedTests implements AllSharedTests {
    private final TestRunner runner = new BytecodeTestRunner();
    
    @Override
    public TestRunner runner() {
        return runner;
    }

    @Override
    public Class<? extends RuntimeException> variableFailException() {
        return CompilationException.class;
    }
}

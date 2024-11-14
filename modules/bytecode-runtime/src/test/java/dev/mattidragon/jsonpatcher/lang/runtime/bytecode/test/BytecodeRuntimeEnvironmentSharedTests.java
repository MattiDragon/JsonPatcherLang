package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;
import dev.mattidragon.jsonpatcher.lang.test.AllSharedTests;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

public class BytecodeRuntimeEnvironmentSharedTests implements AllSharedTests {
    private final TestRunner runner = new EnvironmentTestRunner();
    
    @Override
    public TestRunner runner() {
        return runner;
    }

    @Override
    public Class<? extends RuntimeException> variableFailException() {
        return CompilationException.class;
    }
}

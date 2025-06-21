package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.CompilationException;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.test.AllSharedTests;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class BytecodeRuntimeSharedTests implements AllSharedTests {
    private String testName = "<unknown>";
    private final TestRunner runner = new BytecodeTestRunner(CompilerOptions.DEFAULT, () -> testName);

    @BeforeEach
    public void setTestName(TestInfo testInfo) {
        this.testName = testInfo.getDisplayName();
    }

    @Override
    public TestRunner runner() {
        return runner;
    }

    @Override
    public Class<? extends RuntimeException> variableFailException() {
        return CompilationException.class;
    }
}

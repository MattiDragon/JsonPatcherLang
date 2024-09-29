package dev.mattidragon.jsonpatcher.lang.runtime.legacy.test;

import dev.mattidragon.jsonpatcher.lang.runtime.legacy.EvaluationException;
import dev.mattidragon.jsonpatcher.lang.test.AllSharedTests;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

public class LegacyRuntimeSharedTests implements AllSharedTests {
    private final TestRunner runner = new LegacyTestRunner();
    
    @Override
    public TestRunner runner() {
        return runner;
    }

    @Override
    public Class<? extends RuntimeException> variableFailException() {
        return EvaluationException.class;
    }
}

package dev.mattidragon.jsonpatcher.lang.test;

public interface SharedTest {
    TestRunner runner();
    Class<? extends RuntimeException> variableFailException();
}

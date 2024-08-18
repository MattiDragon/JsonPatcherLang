package io.github.mattidragon.jsonpatcher.lang.test.runtime;

import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;

public abstract class RuntimeTest {
    protected final Runtime runtime;

    protected RuntimeTest(Runtime runtime) {
        this.runtime = runtime;
    }
}

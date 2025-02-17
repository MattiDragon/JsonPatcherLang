package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import org.jspecify.annotations.Nullable;

// Used by reflection in tests
@SuppressWarnings("unused")
public class TestJavaMethods {
    public static final String INFO = "Lorem ipsum";
    public int val;

    public static void staticVoidMethod(int n, String s) {}

    public double instanceMethod(char c, @Nullable Character nullable) {
        val++;
        return c + (nullable != null ? nullable : 0);
    }
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionBody;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionHooks;

public class FunctionTester {
    public static void main(String[] args) {
        var fun = FunctionHooks.createFunction((FunctionBody.F3) (a, b, c) -> {
            throw new RuntimeException();
        }, 2, 3, true);
        var function = (FunctionHooks.DefinedFunction) fun.function();
        System.out.println(function.call(new Value.NumberValue(10), new Value.NumberValue(10)));
    }
}

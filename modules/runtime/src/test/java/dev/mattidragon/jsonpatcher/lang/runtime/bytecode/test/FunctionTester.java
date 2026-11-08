package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionBody;
import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

public class FunctionTester {
    static void main(String[] args) {
        var fun = FunctionHooks.createFunction((FunctionBody.F3) (a, b, c) -> {
            throw new RuntimeException();
        }, 2, 3, true);
        var function = (FunctionHooks.DefinedFunction) fun.function();
        IO.println(function.call(new Value.NumberValue(10), new Value.NumberValue(10)));
    }
}

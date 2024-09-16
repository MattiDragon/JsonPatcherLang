package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.Box;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionBody;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionHooks;

public class Playground {
    int anInt;
    {
        var c = new Box();
        var d = new Box();
        FunctionHooks.createFunction((FunctionBody.F2) (a, b) -> new Value.StringValue("" + a + b + c + d + anInt), 2, 2, false);
    }
}

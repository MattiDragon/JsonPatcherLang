package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.lang.invoke.MethodHandles;

public class ConstantHooks {
    public Value.NumberValue number(MethodHandles.Lookup caller, String name, double value) {
        return new Value.NumberValue(value);
    }
    
    public Value.StringValue string(MethodHandles.Lookup caller, String name, String value) {
        return new Value.StringValue(value);
    }
}

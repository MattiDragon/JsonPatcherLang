package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public class Playground {
    {
        var a = 0;
        System.out.println(++a);
        System.out.println(--a);
    }
}

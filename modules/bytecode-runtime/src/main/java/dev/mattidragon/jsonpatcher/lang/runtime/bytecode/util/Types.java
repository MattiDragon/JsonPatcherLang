package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.BinaryExpressionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.ConstantHooks;
import org.objectweb.asm.Type;

public class Types {
    public static final String VALUE = Type.getInternalName(Value.class);
    public static final String STRING_VALUE = Type.getInternalName(Value.StringValue.class);
    public static final String NUMBER_VALUE = Type.getInternalName(Value.NumberValue.class);
    public static final String BOOLEAN_VALUE = Type.getInternalName(Value.BooleanValue.class);
    public static final String NULL_VALUE = Type.getInternalName(Value.NullValue.class);
    public static final String OBJECT_VALUE = Type.getInternalName(Value.ObjectValue.class);
    public static final String ARRAY_VALUE = Type.getInternalName(Value.ArrayValue.class);
    public static final String FUNCTION_VALUE = Type.getInternalName(Value.FunctionValue.class);
    public static final String BINARY_EXPRESSION_HOOKS = Type.getInternalName(BinaryExpressionHooks.class);
    public static final String CONSTANT_HOOKS = Type.getInternalName(ConstantHooks.class);
    
    private Types() {}
}

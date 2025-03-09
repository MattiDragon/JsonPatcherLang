package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import org.objectweb.asm.Type;

public class Types {
    public static final Type VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value;");
    public static final Type STRING_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$StringValue;");
    public static final Type NUMBER_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$NumberValue;");
    public static final Type BOOLEAN_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$BooleanValue;");
    public static final Type NULL_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$NullValue;");
    public static final Type OBJECT_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$ObjectValue;");
    public static final Type ARRAY_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$ArrayValue;");
    public static final Type FUNCTION_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$FunctionValue;");
    public static final Type SPECIAL_VALUE = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/Value$SpecialValue;");
    public static final Type BINARY_EXPRESSION_HOOKS = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/hooks/BinaryExpressionHooks;");
    public static final Type CONSTANT_HOOKS = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/hooks/ConstantHooks;");
    public static final Type BOX = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/hooks/Box;");
    public static final Type GENERATED_PROGRAM = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/generated/GeneratedProgram;");
    public static final Type FUNCTION_BODY = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/hooks/FunctionBody;");
    public static final Type FUNCTION_HOOKS = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/hooks/FunctionHooks;");
    public static final Type EVALUATION_CONTEXT = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime/EvaluationContext;");
    public static final Type PLATFORM_CONTEXT = Type.getType("Ldev/mattidragon/jsonpatcher/lang/runtime_shared/PlatformContext;");

    private Types() {}
}

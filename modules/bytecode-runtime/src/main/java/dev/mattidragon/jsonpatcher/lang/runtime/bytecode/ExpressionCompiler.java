package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.*;

import java.util.Locale;

public class ExpressionCompiler implements Opcodes {
    // toggle use of dynamic constant for constants
    // condy is probably better for perf (although testing is needed),
    // but it has horrible readability in the decompiled code
    private static final boolean USE_CONDY = false;
    
    private final TreeMetadata metadata;
    private final MethodVisitor visitor;
    private final String className;

    private ExpressionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className) {
        this.metadata = metadata;
        this.visitor = visitor;
        this.className = className;
    }

    public static void compile(Expression expression, TreeMetadata metadata, MethodVisitor visitor, String className) {
        new ExpressionCompiler(metadata, visitor, className).compile(expression);
    }
    
    private void compile(Expression expression) {
        switch (expression) {
            case ValueExpression e -> compileValue(e);
            case IsInstanceExpression e -> compileIsInstance(e);
            case TernaryExpression e -> compileTernary(e);
            case ArrayInitializerExpression e -> compileArrayInit(e);
            case ObjectInitializerExpression e -> compileObjectInit(e);
            case BinaryExpression e -> compileBinary(e);
            case UnaryExpression e -> compileUnary(e);
            default -> throw new UnsupportedOperationException("Unsupported expression: %s".formatted(expression));
        }
    }

    private void compileValue(ValueExpression expression) {
        switch (expression.value()) {
            case Value.BooleanValue value -> visitor.visitFieldInsn(GETSTATIC,
                    Types.BOOLEAN_VALUE,
                    value.value() ? "TRUE" : "FALSE",
                    "Ldev/mattidragon/jsonpatcher/lang/runtime/Value$BooleanValue;");
            case Value.NullValue value -> visitor.visitFieldInsn(GETSTATIC,
                    Types.NULL_VALUE,
                    "NULL",
                    "Ldev/mattidragon/jsonpatcher/lang/runtime/Value$NullValue;");
            case Value.NumberValue(var value) -> {
                if (USE_CONDY) {
                    visitor.visitLdcInsn(new ConstantDynamic("number", 
                            Type.getDescriptor(Value.NumberValue.class),
                            new Handle(H_INVOKESTATIC,
                                    Types.CONSTANT_HOOKS,
                                    "number",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;D)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$NumberValue;",
                                    false),
                            value));
                } else {
                    visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                    visitor.visitInsn(DUP);
                    visitor.visitLdcInsn(value);
                    visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", "(D)V", false);
                }
            }
            case Value.StringValue(var value) -> {
                if (USE_CONDY) {
                    visitor.visitLdcInsn(new ConstantDynamic("string",
                            Type.getDescriptor(Value.NumberValue.class),
                            new Handle(H_INVOKESTATIC,
                                    Types.CONSTANT_HOOKS,
                                    "string",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/String)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$StringValue;",
                                    false),
                            value));
                } else {
                    visitor.visitTypeInsn(NEW, Types.STRING_VALUE);
                    visitor.visitInsn(DUP);
                    visitor.visitLdcInsn(value);
                    visitor.visitMethodInsn(INVOKESPECIAL, Types.STRING_VALUE, "<init>", "(Ljava/lang/String;)V", false);
                }
            }
        }
    }

    private void compileIsInstance(IsInstanceExpression expression) {
        compile(expression.input());
        var clazz = switch (expression.type()) {
            case NUMBER -> Types.NUMBER_VALUE;
            case STRING -> Types.STRING_VALUE;
            case BOOLEAN -> Types.BOOLEAN_VALUE;
            case ARRAY -> Types.ARRAY_VALUE;
            case OBJECT -> Types.OBJECT_VALUE;
            case NULL -> Types.NULL_VALUE;
            case FUNCTION -> Types.FUNCTION_VALUE;
        };
        visitor.visitTypeInsn(INSTANCEOF, clazz);
        visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE, "of", "(Z)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$BooleanValue;", false);
    }

    private void compileTernary(TernaryExpression expression) {
        var endLabel = new Label();
        var falseLabel = new Label();
        compile(expression.condition());
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "asBoolean", "()Z", true);
        visitor.visitJumpInsn(IFEQ, falseLabel);
        compile(expression.ifTrue());
        visitor.visitJumpInsn(GOTO, endLabel);
        visitor.visitLabel(falseLabel);
        compile(expression.ifFalse());
        visitor.visitLabel(endLabel);
    }

    private void compileArrayInit(ArrayInitializerExpression expression) {
        var children = expression.contents();
        visitor.visitTypeInsn(NEW, Types.ARRAY_VALUE);
        visitor.visitInsn(DUP);
        visitor.visitLdcInsn(children.size());
        visitor.visitTypeInsn(ANEWARRAY, Types.VALUE);
        for (int i = 0; i < children.size(); i++) {
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn(i);
            compile(children.get(i));
            visitor.visitInsn(AASTORE);
        }
        visitor.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
        visitor.visitMethodInsn(INVOKESPECIAL, Types.ARRAY_VALUE, "<init>", "(Ljava/util/List;)V", false);
    }

    private void compileObjectInit(ObjectInitializerExpression expression) {
        var children = expression.contents();
        visitor.visitTypeInsn(NEW, Types.OBJECT_VALUE);
        visitor.visitInsn(DUP);
        visitor.visitTypeInsn(NEW, "java/util/HashMap");
        visitor.visitInsn(DUP);
        visitor.visitLdcInsn(children.size());
        visitor.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "(I)V", false);
        for (var entry : children) {
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn(entry.name());
            compile(entry.value());
            visitor.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            visitor.visitInsn(POP);
        }
        visitor.visitMethodInsn(INVOKESPECIAL, Types.OBJECT_VALUE, "<init>", "(Ljava/util/Map;)V", false);
    }

    private void compileBinary(BinaryExpression e) {
        compile(e.first());
        compile(e.second());
        visitor.visitInsn(ACONST_NULL);
        visitor.visitVarInsn(ALOAD, 0);
        visitor.visitFieldInsn(GETFIELD, className, "context", Type.getDescriptor(EvaluationContext.class));
        visitor.visitInvokeDynamicInsn(e.op().name().toLowerCase(Locale.ROOT), 
                Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.class), Type.getType(Value.class), Type.getType(SourceSpan.class), Type.getType(EvaluationContext.class)),
                new Handle(H_INVOKESTATIC, Types.BINARY_EXPRESSION_HOOKS, "hook", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false));
    }

    private void compileUnary(UnaryExpression e) {
        compile(e.input());
        compileUnaryOp(e.op());
    }

    private void compileUnaryOp(UnaryExpression.Operator op) {
        switch (op) {
            case NOT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE, "value", "()Z", false);
                var midLabel = new Label();
                var endLabel = new Label();
                visitor.visitJumpInsn(IFNE, midLabel);
                visitor.visitLdcInsn(1);
                visitor.visitJumpInsn(GOTO, endLabel);
                visitor.visitLabel(midLabel);
                visitor.visitLdcInsn(0);
                visitor.visitLabel(endLabel);
                visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE, "of", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(Value.BooleanValue.class)), false);
            }
            case MINUS -> {
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(DNEG);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(Value.NumberValue.class)), false);
            }
            case BITWISE_NOT -> {
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_M1);
                visitor.visitInsn(IXOR);
                visitor.visitInsn(I2D);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(Value.NumberValue.class)), false);
            }
            case INCREMENT -> {
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(IADD);
                visitor.visitInsn(I2D);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(Value.NumberValue.class)), false);
            }
            case DECREMENT -> {
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(ISUB);
                visitor.visitInsn(I2D);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.DOUBLE_TYPE, Type.getType(Value.NumberValue.class)), false);
            }
        }
    }
}

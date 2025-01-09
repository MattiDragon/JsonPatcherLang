package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Scope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.Box;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionBody;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import org.objectweb.asm.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;

public class ExpressionCompiler implements Opcodes {
    private final TreeMetadata metadata;
    private final MethodVisitor visitor;
    private final String className;
    private final FunctionCompiler functionCompiler;

    public ExpressionCompiler(TreeMetadata metadata, MethodVisitor visitor, String className, FunctionCompiler functionCompiler) {
        this.metadata = metadata;
        this.visitor = visitor;
        this.className = className;
        this.functionCompiler = functionCompiler;
    }

    public void compile(Expression expression) {
        metadata.get(expression, MetadataKey.MAIN_POS).ifPresent(sourceSpan -> functionCompiler.emitLineNumber(sourceSpan.from().row()));
        var constantValue = metadata.get(expression, ConstantAnalyser.CONSTANT_VALUE);
        if (functionCompiler.options().useConstantFolding && constantValue.isPresent()) {
            compileValue(constantValue.get());
            return;
        }

        switch (expression) {
            case PrimitiveExpression e -> compileValue(e.value());
            case IsInstanceExpression e -> compileIsInstance(e);
            case TernaryExpression e -> compileTernary(e);
            case ArrayInitializerExpression e -> compileArrayInit(e);
            case ObjectInitializerExpression e -> compileObjectInit(e);
            case BinaryExpression e -> compileBinary(e);
            case ShortedBinaryExpression e -> compileShortedBinary(e);
            case UnaryExpression e -> compileUnary(e);
            case UnaryModificationExpression e -> compileUnaryModification(e);
            case VariableAccessExpression e -> compileVariableAccess(e);
            case PropertyAccessExpression e -> compilePropertyAccess(e);
            case IndexExpression e -> compileIndex(e);
            case AssignmentExpression e -> compileAssignment(e);
            case RootExpression e -> compileRoot(e);
            case FunctionExpression e -> compileFunction(e);
            case FunctionCallExpression e -> compileFunctionCall(e);
            default -> throw new UnsupportedOperationException("Unsupported expression: %s".formatted(expression));
        }
    }

    private void compileValue(Value.Primitive value) {
        switch (value) {
            case Value.BooleanValue booleanValue -> visitor.visitFieldInsn(GETSTATIC,
                    Types.BOOLEAN_VALUE,
                    booleanValue.value() ? "TRUE" : "FALSE",
                    "Ldev/mattidragon/jsonpatcher/lang/runtime/Value$BooleanValue;");
            case Value.NullValue.NULL -> visitor.visitFieldInsn(GETSTATIC,
                    Types.NULL_VALUE,
                    "NULL",
                    "Ldev/mattidragon/jsonpatcher/lang/runtime/Value$NullValue;");
            case Value.NumberValue(var number) -> {
                if (functionCompiler.options().useDynamicConstants) {
                    visitor.visitLdcInsn(new ConstantDynamic("number", 
                            Type.getDescriptor(Value.NumberValue.class),
                            new Handle(H_INVOKESTATIC,
                                    Types.CONSTANT_HOOKS,
                                    "number",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;D)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$NumberValue;",
                                    false),
                            number));
                } else {
                    visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                    visitor.visitInsn(DUP);
                    visitor.visitLdcInsn(number);
                    visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", "(D)V", false);
                }
            }
            case Value.StringValue(var string) -> {
                if (functionCompiler.options().useDynamicConstants) {
                    visitor.visitLdcInsn(new ConstantDynamic("string",
                            Type.getDescriptor(Value.StringValue.class),
                            new Handle(H_INVOKESTATIC,
                                    Types.CONSTANT_HOOKS,
                                    "string",
                                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;Ljava/lang/String;)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$StringValue;",
                                    false),
                            string));
                } else {
                    visitor.visitTypeInsn(NEW, Types.STRING_VALUE);
                    visitor.visitInsn(DUP);
                    visitor.visitLdcInsn(string);
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
        compileBinaryOp(e.op());
    }

    private void compileUnary(UnaryExpression e) {
        compile(e.input());
        compileUnaryOp(e.op());
    }

    private void compileShortedBinary(ShortedBinaryExpression expression) {
        compile(expression.first());
        visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE);
        visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE, "value", "()Z", false);
        var trueLabel = new Label();
        var falseLabel = new Label();
        var endLabel = new Label();
        
        switch (expression.op()) {
            case AND -> visitor.visitJumpInsn(IFEQ, falseLabel);
            case OR -> visitor.visitJumpInsn(IFNE, trueLabel);
        }
        
        compile(expression.second());
        visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE);
        visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE, "value", "()Z", false);
        
        visitor.visitJumpInsn(IFEQ, falseLabel);
        
        visitor.visitLabel(trueLabel);
        visitor.visitInsn(ICONST_1);
        visitor.visitJumpInsn(GOTO, endLabel);
        visitor.visitLabel(falseLabel);
        visitor.visitInsn(ICONST_0);
        
        visitor.visitLabel(endLabel);
        visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE, "of", Type.getMethodDescriptor(Type.getType(Value.BooleanValue.class), Type.BOOLEAN_TYPE), false);
    }

    private void compileUnaryModification(UnaryModificationExpression expression) {
        var op = expression.operator();
        switch (expression.target()) {
            case VariableAccessExpression accessExpression -> {
                var variable = metadata.get(accessExpression, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow();
                
                compile(accessExpression);
                
                if (expression.postfix()) visitor.visitInsn(DUP);
                compileUnaryOp(op);
                if (!expression.postfix()) visitor.visitInsn(DUP);
                
                if (variable.isCaptured()) {
                    visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(variable));
                    visitor.visitInsn(SWAP);
                    visitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Box.class), "setValue", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class)), false);
                } else {
                    visitor.visitVarInsn(ASTORE, functionCompiler.getOrAllocateVariable(variable));
                }
            }
            case PropertyAccessExpression e -> {
                compile(e.parent());
                visitor.visitLdcInsn(e.name());

                visitor.visitInsn(DUP2);
                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "getProperty", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(String.class), Type.getType(PlatformContext.class)), true);
                
                if (expression.postfix()) visitor.visitInsn(DUP_X2);
                compileUnaryOp(op);
                if (!expression.postfix()) visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "setProperty", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
            }
            case IndexExpression e -> {
                compile(e.parent());
                compile(e.index());
                
                visitor.visitInsn(DUP2);
                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "get", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);

                if (expression.postfix()) visitor.visitInsn(DUP_X2);
                compileUnaryOp(op);
                if (!expression.postfix()) visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "set", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
            }
            default -> throw new IllegalStateException("Unsupported assignment target: " + expression.target());
        }
    }

    private void compileVariableAccess(VariableAccessExpression expression) {
        var variable = metadata.get(expression, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow();
        visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(variable));
        if (variable.isCaptured()) {
            visitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Box.class), "getValue", Type.getMethodDescriptor(Type.getType(Value.class)), false);
        }
    }

    private void compilePropertyAccess(PropertyAccessExpression expression) {
        compile(expression.parent());
        visitor.visitLdcInsn(expression.name());
        functionCompiler.loadContext();
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "getProperty", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(String.class), Type.getType(PlatformContext.class)), true);
    }

    private void compileIndex(IndexExpression expression) {
        compile(expression.parent());
        compile(expression.index());
        functionCompiler.loadContext();
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "get", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
    }

    private void compileAssignment(AssignmentExpression expression) {
        var op = expression.operator();
        switch (expression.target()) {
            case VariableAccessExpression accessExpression -> {
                var variable = metadata.get(accessExpression, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow();
                if (op == BinaryExpression.Operator.ASSIGN) {
                    compile(expression.value());
                } else {
                    compile(accessExpression);
                    compile(expression.value());
                    compileBinaryOp(op);
                }
                visitor.visitInsn(DUP);
                if (variable.isCaptured()) {
                    visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(variable));
                    visitor.visitInsn(SWAP);
                    visitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(Box.class), "setValue", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class)), false);
                } else {
                    visitor.visitVarInsn(ASTORE, functionCompiler.getOrAllocateVariable(variable));
                }
            }
            case PropertyAccessExpression e -> {
                compile(e.parent());
                visitor.visitLdcInsn(e.name());
                
                if (op == BinaryExpression.Operator.ASSIGN) {
                    compile(expression.value());
                } else {
                    visitor.visitInsn(DUP2);
                    functionCompiler.loadContext();
                    visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "getProperty", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(String.class), Type.getType(PlatformContext.class)), true);
                    compile(expression.value());
                    compileBinaryOp(op);
                }
                
                visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "setProperty", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
            }
            case IndexExpression e -> {
                compile(e.parent());
                compile(e.index());

                if (op == BinaryExpression.Operator.ASSIGN) {
                    compile(expression.value());
                } else {
                    visitor.visitInsn(DUP2);
                    functionCompiler.loadContext();
                    visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "get", Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
                    compile(expression.value());
                    compileBinaryOp(op);
                }

                visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE, "set", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.class), Type.getType(Value.class), Type.getType(PlatformContext.class)), true);
            }
            default -> throw new IllegalStateException("Unsupported assignment target: " + expression.target());
        }
    }

    private void compileRoot(RootExpression e) {
        var root = metadata.get(e,  VariableAnalyser.ROOT_REFERENCE).orElseThrow();
        visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateRoot(root));
    }

    private void compileUnaryOp(UnaryExpression.Operator op) {
        switch (op) {
            case NOT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE, "value", "()Z", false);
                var midLabel = new Label();
                var endLabel = new Label();
                visitor.visitJumpInsn(IFNE, midLabel);
                visitor.visitInsn(ICONST_1);
                visitor.visitJumpInsn(GOTO, endLabel);
                visitor.visitLabel(midLabel);
                visitor.visitInsn(ICONST_0);
                visitor.visitLabel(endLabel);
                visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE, "of", Type.getMethodDescriptor(Type.getType(Value.BooleanValue.class), Type.BOOLEAN_TYPE), false);
            }
            case MINUS -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(DNEG);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case BITWISE_NOT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_M1);
                visitor.visitInsn(IXOR);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case INCREMENT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(IADD);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case DECREMENT -> {
                //visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE, "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(ISUB);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE, "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
        }
    }

    private void compileFunction(FunctionExpression expression) {
        var scope = (FunctionScope) metadata.get(expression, VariableAnalyser.SCOPE).orElseThrow();

        var capturesRoot = expression.args().arguments().stream().noneMatch(argument -> argument.target() instanceof FunctionArgument.Target.Root);
        var argCount = expression.args().arguments().size();
        var interfaceMethodType = Type.getMethodType("(%s)L%s;".formatted(("L" + Types.VALUE + ";").repeat(argCount), Types.VALUE));

        var targetType = new StringBuilder("(");
        targetType.append(("L" + Types.BOX + ";").repeat(scope.captures().size()));
        if (capturesRoot) {
            targetType.append("L").append(Types.OBJECT_VALUE).append(";");
        }
        for (var argument : expression.args().arguments()) {
            switch (argument.target()) {
                case FunctionArgument.Target.Root.INSTANCE -> targetType.append("L").append(Types.VALUE).append(";");
                case FunctionArgument.Target.Variable variable -> targetType.append("L").append(Types.VALUE).append(";");
            }
        }
        targetType.append(")L").append(Types.VALUE).append(";");

        var invokerType = new StringBuilder("(");
        invokerType.append("L").append(className).append(";");
        for (int i = 0; i < scope.captures().size(); i++) {
            invokerType.append("L").append(Types.BOX).append(";");
        }
        if (capturesRoot) {
            invokerType.append("L").append(Types.OBJECT_VALUE).append(";");
        }
        invokerType.append(")").append("L").append(Type.getInternalName(FunctionBody.class)).append("$F").append(argCount).append(";");
        
        visitor.visitVarInsn(ALOAD, 0);
        for (var capture : scope.captures()) {
            visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(capture));
        }
        if (capturesRoot) {
            visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateRoot(((Scope) scope.parent()).root()));
        }

        visitor.visitInvokeDynamicInsn("call",
                invokerType.toString(),
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(LambdaMetafactory.class),
                        "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
                        false),
                interfaceMethodType,
                new Handle(H_INVOKEVIRTUAL,
                        className,
                        functionCompiler.getLambdaName(expression),
                        targetType.toString(),
                        false),
                interfaceMethodType);
        visitor.visitLdcInsn(expression.args().requiredArguments());
        visitor.visitLdcInsn(expression.args().arguments().size());
        visitor.visitLdcInsn(expression.args().varargs());
        visitor.visitMethodInsn(INVOKESTATIC,
                Type.getInternalName(FunctionHooks.class),
                "createFunction",
                "(Ldev/mattidragon/jsonpatcher/lang/runtime/bytecode/hooks/FunctionBody;IIZ)Ldev/mattidragon/jsonpatcher/lang/runtime/Value$FunctionValue;",
                false);
    }

    private void compileFunctionCall(FunctionCallExpression expression) {
        functionCompiler.loadContext();
        compile(expression.function());
        for (var argument : expression.arguments()) {
            compile(argument);
        }

        var invokerType = new StringBuilder("(");
        invokerType.append(Type.getDescriptor(PlatformContext.class));
        invokerType.append(Type.getDescriptor(Value.class));
        for (int i = 0; i < expression.arguments().size(); i++) {
            invokerType.append(Type.getDescriptor(Value.class));
        }
        invokerType.append(")").append(Type.getDescriptor(Value.class));

        visitor.visitInvokeDynamicInsn("function",
                invokerType.toString(),
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(FunctionHooks.class),
                        "callHook",
                        Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class)),
                        false));
    }

    private void compileBinaryOp(BinaryExpression.Operator op) {
        visitor.visitInvokeDynamicInsn(op.name().toLowerCase(Locale.ROOT),
                Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Value.class), Type.getType(Value.class)),
                new Handle(H_INVOKESTATIC, Types.BINARY_EXPRESSION_HOOKS, "hook", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false));
    }
}

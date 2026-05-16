package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantAnalyser;
import dev.mattidragon.jsonpatcher.lang.analysis.constant.ConstantValue;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.FunctionScope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.Types;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util.VariableUtil;
import org.objectweb.asm.*;

import java.lang.invoke.*;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

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
            case NumberExpression(var num) -> compileNumber(num);
            case StringExpression(var s) -> compileString(s);
            case BooleanExpression(var bl) -> compileBoolean(bl);
            case NullExpression() -> compileNull();
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
            case StringInterpolationExpression e -> compileStringInterpolation(e);
            default -> throw new UnsupportedOperationException("Unsupported expression: %s".formatted(expression));
        }
    }

    private void compileValue(ConstantValue value) {
        switch (value) {
            case ConstantValue.Boolean booleanValue -> compileBoolean(booleanValue.value());
            case ConstantValue.Null.NULL -> compileNull();
            case ConstantValue.Number(var number) -> compileNumber(number);
            case ConstantValue.String(var string) -> compileString(string);
        }
    }

    private void compileString(String string) {
        if (functionCompiler.options().useDynamicConstants) {
            var descriptor = Type.getMethodDescriptor(
                    Types.STRING_VALUE,
                    Type.getType(MethodHandles.Lookup.class),
                    Type.getType(String.class),
                    Type.getType(Class.class),
                    Type.getType(String.class)
            );
            visitor.visitLdcInsn(new ConstantDynamic("string",
                    Types.STRING_VALUE.getDescriptor(), 
                    new Handle(H_INVOKESTATIC,
                            Types.CONSTANT_HOOKS.getInternalName(),
                            "string",
                            descriptor,
                            false),
                    string));
        } else {
            visitor.visitTypeInsn(NEW, Types.STRING_VALUE.getInternalName());
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn(string);
            visitor.visitMethodInsn(INVOKESPECIAL, Types.STRING_VALUE.getInternalName(), "<init>", "(Ljava/lang/String;)V", false);
        }
    }

    private void compileNumber(double number) {
        if (functionCompiler.options().useDynamicConstants) {
            var descriptor = Type.getMethodDescriptor(
                    Types.NUMBER_VALUE,
                    Type.getType(MethodHandles.Lookup.class),
                    Type.getType(String.class),
                    Type.getType(Class.class),
                    Type.DOUBLE_TYPE
            );
            visitor.visitLdcInsn(new ConstantDynamic("number",
                    Types.NUMBER_VALUE.getDescriptor(),
                    new Handle(H_INVOKESTATIC,
                            Types.CONSTANT_HOOKS.getInternalName(),
                            "number",
                            descriptor,
                            false),
                    number));
        } else {
            visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE.getInternalName());
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn(number);
            visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE.getInternalName(), "<init>", "(D)V", false);
        }
    }

    private void compileNull() {
        visitor.visitFieldInsn(GETSTATIC,
                Types.NULL_VALUE.getInternalName(),
                "NULL",
                Types.NULL_VALUE.getDescriptor());
    }

    private void compileBoolean(boolean value) {
        visitor.visitFieldInsn(GETSTATIC,
                Types.BOOLEAN_VALUE.getInternalName(),
                value ? "TRUE" : "FALSE",
                Types.BOOLEAN_VALUE.getDescriptor());
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
            case SPECIAL -> Types.SPECIAL_VALUE;
        };
        visitor.visitTypeInsn(INSTANCEOF, clazz.getInternalName());
        visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE.getInternalName(), "of", "(Z)" + Types.BOOLEAN_VALUE.getDescriptor(), false);
    }

    private void compileTernary(TernaryExpression expression) {
        var endLabel = new Label();
        var falseLabel = new Label();
        compile(expression.condition());
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "asBoolean", "()Z", true);
        visitor.visitJumpInsn(IFEQ, falseLabel);
        compile(expression.ifTrue());
        visitor.visitJumpInsn(GOTO, endLabel);
        visitor.visitLabel(falseLabel);
        compile(expression.ifFalse());
        visitor.visitLabel(endLabel);
    }

    private void compileArrayInit(ArrayInitializerExpression expression) {
        var children = expression.contents();
        visitor.visitTypeInsn(NEW, Types.ARRAY_VALUE.getInternalName());
        visitor.visitInsn(DUP);
        visitor.visitLdcInsn(children.size());
        visitor.visitTypeInsn(ANEWARRAY, Types.VALUE.getInternalName());
        for (int i = 0; i < children.size(); i++) {
            visitor.visitInsn(DUP);
            visitor.visitLdcInsn(i);
            compile(children.get(i));
            visitor.visitInsn(AASTORE);
        }
        visitor.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false);
        visitor.visitMethodInsn(INVOKESPECIAL, Types.ARRAY_VALUE.getInternalName(), "<init>", "(Ljava/util/List;)V", false);
    }

    private void compileObjectInit(ObjectInitializerExpression expression) {
        var children = expression.contents();
        visitor.visitTypeInsn(NEW, Types.OBJECT_VALUE.getInternalName());
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
        visitor.visitMethodInsn(INVOKESPECIAL, Types.OBJECT_VALUE.getInternalName(), "<init>", "(Ljava/util/Map;)V", false);
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
        visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE.getInternalName());
        visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE.getInternalName(), "value", "()Z", false);
        var trueLabel = new Label();
        var falseLabel = new Label();
        var endLabel = new Label();
        
        switch (expression.op()) {
            case AND -> visitor.visitJumpInsn(IFEQ, falseLabel);
            case OR -> visitor.visitJumpInsn(IFNE, trueLabel);
        }
        
        compile(expression.second());
        visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE.getInternalName());
        visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE.getInternalName(), "value", "()Z", false);
        
        visitor.visitJumpInsn(IFEQ, falseLabel);
        
        visitor.visitLabel(trueLabel);
        visitor.visitInsn(ICONST_1);
        visitor.visitJumpInsn(GOTO, endLabel);
        visitor.visitLabel(falseLabel);
        visitor.visitInsn(ICONST_0);
        
        visitor.visitLabel(endLabel);
        visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE.getInternalName(), "of", Type.getMethodDescriptor(Types.BOOLEAN_VALUE, Type.BOOLEAN_TYPE), false);
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
                
                if (VariableUtil.needsBoxing(variable, metadata)) {
                    visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(variable));
                    visitor.visitInsn(SWAP);
                    visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOX.getInternalName(), "setValue", Type.getMethodDescriptor(Type.VOID_TYPE, Types.VALUE), false);
                } else {
                    visitor.visitVarInsn(ASTORE, functionCompiler.getOrAllocateVariable(variable));
                }
            }
            case PropertyAccessExpression e -> {
                compile(e.parent());
                visitor.visitLdcInsn(e.name());

                visitor.visitInsn(DUP2);
                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "getProperty", Type.getMethodDescriptor(Types.VALUE, Type.getType(String.class), Types.PLATFORM_CONTEXT), true);
                
                if (expression.postfix()) visitor.visitInsn(DUP_X2);
                compileUnaryOp(op);
                if (!expression.postfix()) visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "setProperty", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Types.VALUE, Types.PLATFORM_CONTEXT), true);
            }
            case IndexExpression e -> {
                compile(e.parent());
                compile(e.index());
                
                visitor.visitInsn(DUP2);
                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "get", Type.getMethodDescriptor(Types.VALUE, Types.VALUE, Types.PLATFORM_CONTEXT), true);

                if (expression.postfix()) visitor.visitInsn(DUP_X2);
                compileUnaryOp(op);
                if (!expression.postfix()) visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "set", Type.getMethodDescriptor(Type.VOID_TYPE, Types.VALUE, Types.VALUE, Types.PLATFORM_CONTEXT), true);
            }
            default -> throw new IllegalStateException("Unsupported assignment target: " + expression.target());
        }
    }

    private void compileVariableAccess(VariableAccessExpression expression) {
        var variable = metadata.get(expression, VariableAnalyser.VARIABLE_REFERENCE).orElseThrow();
        visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(variable));
        if (VariableUtil.needsBoxing(variable, metadata)) {
            visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOX.getInternalName(), "getValue", Type.getMethodDescriptor(Types.VALUE), false);
            visitor.visitLdcInsn("Variable %s accessed before initialization".formatted(variable.name()));
            visitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Objects.class), "requireNonNull",
                    Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class), Type.getType(String.class)), false);
            visitor.visitTypeInsn(CHECKCAST, Types.VALUE.getInternalName());
        }
    }

    private void compilePropertyAccess(PropertyAccessExpression expression) {
        compile(expression.parent());
        visitor.visitLdcInsn(expression.name());
        functionCompiler.loadContext();
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "getProperty", Type.getMethodDescriptor(Types.VALUE, Type.getType(String.class), Types.PLATFORM_CONTEXT), true);
    }

    private void compileIndex(IndexExpression expression) {
        compile(expression.parent());
        compile(expression.index());
        functionCompiler.loadContext();
        visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "get", Type.getMethodDescriptor(Types.VALUE, Types.VALUE, Types.PLATFORM_CONTEXT), true);
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
                functionCompiler.compileVariableAssignment(variable);
            }
            case PropertyAccessExpression e -> {
                compile(e.parent());
                visitor.visitLdcInsn(e.name());
                
                if (op == BinaryExpression.Operator.ASSIGN) {
                    compile(expression.value());
                } else {
                    visitor.visitInsn(DUP2);
                    functionCompiler.loadContext();
                    visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "getProperty", Type.getMethodDescriptor(Types.VALUE, Type.getType(String.class), Types.PLATFORM_CONTEXT), true);
                    compile(expression.value());
                    compileBinaryOp(op);
                }
                
                visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "setProperty", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Types.VALUE, Types.PLATFORM_CONTEXT), true);
            }
            case IndexExpression e -> {
                compile(e.parent());
                compile(e.index());

                if (op == BinaryExpression.Operator.ASSIGN) {
                    compile(expression.value());
                } else {
                    visitor.visitInsn(DUP2);
                    functionCompiler.loadContext();
                    visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "get", Type.getMethodDescriptor(Types.VALUE, Types.VALUE, Types.PLATFORM_CONTEXT), true);
                    compile(expression.value());
                    compileBinaryOp(op);
                }

                visitor.visitInsn(DUP_X2);

                functionCompiler.loadContext();
                visitor.visitMethodInsn(INVOKEINTERFACE, Types.VALUE.getInternalName(), "set", Type.getMethodDescriptor(Type.VOID_TYPE, Types.VALUE, Types.VALUE, Types.PLATFORM_CONTEXT), true);
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
                visitor.visitTypeInsn(CHECKCAST, Types.BOOLEAN_VALUE.getInternalName()); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.BOOLEAN_VALUE.getInternalName(), "value", "()Z", false);
                var midLabel = new Label();
                var endLabel = new Label();
                visitor.visitJumpInsn(IFNE, midLabel);
                visitor.visitInsn(ICONST_1);
                visitor.visitJumpInsn(GOTO, endLabel);
                visitor.visitLabel(midLabel);
                visitor.visitInsn(ICONST_0);
                visitor.visitLabel(endLabel);
                visitor.visitMethodInsn(INVOKESTATIC, Types.BOOLEAN_VALUE.getInternalName(), "of", Type.getMethodDescriptor(Types.BOOLEAN_VALUE, Type.BOOLEAN_TYPE), false);
            }
            case MINUS -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE.getInternalName()); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE.getInternalName(), "value", "()D", false);
                visitor.visitInsn(DNEG);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE.getInternalName());
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case BITWISE_NOT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE.getInternalName()); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE.getInternalName(), "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_M1);
                visitor.visitInsn(IXOR);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE.getInternalName());
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case INCREMENT -> {
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE.getInternalName()); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE.getInternalName(), "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(IADD);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE.getInternalName());
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
            case DECREMENT -> {
                //visitor.visitInsn(DUP);
                visitor.visitTypeInsn(CHECKCAST, Types.NUMBER_VALUE.getInternalName()); // TODO: custom cast logic?
                visitor.visitMethodInsn(INVOKEVIRTUAL, Types.NUMBER_VALUE.getInternalName(), "value", "()D", false);
                visitor.visitInsn(D2I);
                visitor.visitInsn(ICONST_1);
                visitor.visitInsn(ISUB);
                visitor.visitInsn(I2D);
                visitor.visitTypeInsn(NEW, Types.NUMBER_VALUE.getInternalName());
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(DUP_X2);
                visitor.visitInsn(POP);
                visitor.visitMethodInsn(INVOKESPECIAL, Types.NUMBER_VALUE.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.DOUBLE_TYPE), false);
            }
        }
    }

    private void compileFunction(FunctionExpression expression) {
        var scope = (FunctionScope) metadata.get(expression, VariableAnalyser.SCOPE).orElseThrow();

        var capturesRoot = expression.args().arguments().stream().noneMatch(argument -> argument.target() instanceof FunctionArgument.Target.Root);
        var argCount = expression.args().arguments().size();
        var interfaceMethodType = Type.getMethodType("(%s)%s".formatted(Types.VALUE.getDescriptor().repeat(argCount), Types.VALUE.getDescriptor()));

        var targetType = new StringBuilder("(");
        for (var capture : scope.captures()) {
            if (VariableUtil.needsBoxing(capture, metadata)) {
                targetType.append(Types.BOX.getDescriptor());
            } else {
                targetType.append(Types.VALUE.getDescriptor());
            }
        }
        if (capturesRoot) {
            targetType.append(Types.OBJECT_VALUE.getDescriptor());
        }
        for (var argument : expression.args().arguments()) {
            switch (argument.target()) {
                case FunctionArgument.Target.Root.INSTANCE -> targetType.append(Types.VALUE.getDescriptor());
                case FunctionArgument.Target.Variable variable -> targetType.append(Types.VALUE.getDescriptor());
            }
        }
        targetType.append(")").append(Types.VALUE.getDescriptor());

        var invokerType = new StringBuilder("(");
        invokerType.append("L").append(className).append(";");
        for (var capture : scope.captures()) {
            if (VariableUtil.needsBoxing(capture, metadata)) {
                invokerType.append(Types.BOX.getDescriptor());
            } else {
                invokerType.append(Types.VALUE.getDescriptor());
            }
        }
        if (capturesRoot) {
            invokerType.append(Types.OBJECT_VALUE.getDescriptor());
        }
        invokerType.append(")").append("L").append(Types.FUNCTION_BODY.getInternalName()).append("$F").append(argCount).append(";");
        
        visitor.visitVarInsn(ALOAD, 0);
        for (var capture : scope.captures()) {
            visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateVariable(capture));
        }
        if (capturesRoot) {
            visitor.visitVarInsn(ALOAD, functionCompiler.getOrAllocateRoot(scope.parent().root()));
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
        var descriptor = Type.getMethodDescriptor(Types.FUNCTION_VALUE, Types.FUNCTION_BODY, Type.INT_TYPE, Type.INT_TYPE, Type.BOOLEAN_TYPE);
        visitor.visitMethodInsn(INVOKESTATIC,
                Types.FUNCTION_HOOKS.getInternalName(),
                "createFunction",
                descriptor,
                false);
    }

    private void compileFunctionCall(FunctionCallExpression expression) {
        functionCompiler.loadContext();
        compile(expression.function());
        for (var argument : expression.arguments()) {
            compile(argument);
        }

        var invokerType = "(%s%s%s)%s".formatted(
                Types.PLATFORM_CONTEXT,
                Types.VALUE,
                String.valueOf(Types.VALUE).repeat(expression.arguments().size()),
                Types.VALUE
        );

        visitor.visitInvokeDynamicInsn("function",
                invokerType,
                new Handle(H_INVOKESTATIC,
                        Types.FUNCTION_HOOKS.getInternalName(),
                        "callHook",
                        Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class)),
                        false));
    }

    private void compileStringInterpolation(StringInterpolationExpression e) {
        visitor.visitTypeInsn(NEW, Types.STRING_VALUE.getInternalName());
        visitor.visitInsn(DUP);

        for (var child : e.children()) {
            compile(child);
            visitor.visitMethodInsn(INVOKESTATIC,
                    Types.STRING_HOOKS.getInternalName(),
                    "asString",
                    Type.getMethodDescriptor(Type.getType(String.class), Types.VALUE),
                    false);
        }

        var invokerType = "(%s)%s".formatted(
                Type.getType(String.class).getDescriptor().repeat(e.children().size()),
                Type.getType(String.class).getDescriptor()
        );

        var bootstrapMethodArgs = new Object[e.parts().size() + 1];
        bootstrapMethodArgs[0] = e.parts().stream().map(part -> "\2").collect(Collectors.joining("\1"));
        for (var i = 0; i < e.parts().size(); i++) {
            bootstrapMethodArgs[i + 1] = e.parts().get(i);
        }

        visitor.visitInvokeDynamicInsn(
                "stringInterpolation",
                invokerType,
                new Handle(H_INVOKESTATIC,
                        Type.getInternalName(StringConcatFactory.class),
                        "makeConcatWithConstants",
                        Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class), Type.getType(String.class), Type.getType(Object[].class)),
                        false),
                bootstrapMethodArgs
        );
        visitor.visitMethodInsn(INVOKESPECIAL, Types.STRING_VALUE.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class)), false);
    }

    private void compileBinaryOp(BinaryExpression.Operator op) {
        var descriptor = Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class));
        visitor.visitInvokeDynamicInsn(op.name().toLowerCase(Locale.ROOT),
                Type.getMethodDescriptor(Types.VALUE, Types.VALUE, Types.VALUE),
                new Handle(H_INVOKESTATIC, Types.BINARY_EXPRESSION_HOOKS.getInternalName(), "hook", descriptor, false));
    }
}

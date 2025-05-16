package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.objectweb.asm.*;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Generates bridge classes to use jsonpatcher functions as lambdas in java
 */
public class LambdaBridgeGenerator {
    private static final Map<Class<?>, LambdaBridgeGenerator> CACHE = new WeakHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final String CLASS_NAME = LambdaBridgeGenerator.class.getPackageName().replace(".", "/") + "/LambdaBridge";

    private final MethodHandle constructor;

    private LambdaBridgeGenerator(MethodHandle constructor) {
        this.constructor = constructor;
    }

    public static <T> T createLambdaBridge(Class<T> interfaceClass, EvaluationContext context, Value.FunctionValue function) {
        if (CACHE.containsKey(interfaceClass)) {
            return getFromCache(interfaceClass, context, function);
        }

        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Can only convert lambdas to interfaces");
        }
        var abstractMethods = Arrays.stream(interfaceClass.getDeclaredMethods())
                .filter(m -> m.accessFlags().contains(AccessFlag.ABSTRACT))
                .toList();
        if (abstractMethods.size() != 1) {
            throw new IllegalArgumentException("Interface must have exactly one abstract method");
        }

        var sam = abstractMethods.getFirst();

        var bytes = genBridgeClass(interfaceClass, sam);
        LambdaBridgeGenerator generator;
        try {
            var clazz = LOOKUP.defineHiddenClass(bytes, true).lookupClass();
            var constructor = LOOKUP.findConstructor(clazz, MethodType.methodType(void.class, Value.FunctionValue.class, EvaluationContext.class));
            generator = new LambdaBridgeGenerator(constructor);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Don't have permission to create lambda class", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Could not find constructor that we just created", e);
        }

        CACHE.put(interfaceClass, generator);
        return getFromCache(interfaceClass, context, function);
    }

    private static byte[] genBridgeClass(Class<?> interfaceClass, Method sam) {
        // anon class for correct classloader
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
        };
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, CLASS_NAME, null, "java/lang/Object", new String[]{Type.getInternalName(interfaceClass)});

        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "impl",
                Type.getDescriptor(Value.FunctionValue.class),
                null,
                null
        );
        writer.visitField(
                Opcodes.ACC_PRIVATE,
                "context",
                Type.getDescriptor(EvaluationContext.class),
                null,
                null
        );

        genConstructor(writer);
        genImplMethod(writer, sam);

        return writer.toByteArray();
    }

    private static void genImplMethod(ClassWriter writer, Method sam) {
        var implMethod = writer.visitMethod(
                Opcodes.ACC_PUBLIC,
                sam.getName(),
                Type.getMethodDescriptor(sam),
                null,
                null
        );

        implMethod.visitVarInsn(Opcodes.ALOAD, 0);
        implMethod.visitFieldInsn(Opcodes.GETFIELD, CLASS_NAME, "context", Type.getDescriptor(EvaluationContext.class));
        implMethod.visitVarInsn(Opcodes.ALOAD, 0);
        implMethod.visitFieldInsn(Opcodes.GETFIELD, CLASS_NAME, "impl", Type.getDescriptor(Value.FunctionValue.class));

        for (var i = 0; i < sam.getParameterCount(); i++) {
            implMethod.visitVarInsn(Opcodes.ALOAD, i + 1);
            implMethod.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(JavaValueUtil.class),
                    "objectToValue",
                    Type.getMethodDescriptor(Type.getType(Value.class), Type.getType(Object.class)),
                    false
            );
        }

        var invokerType = "(%s%s%s)%s".formatted(
                Type.getType(EvaluationContext.class),
                Type.getType(Value.class),
                String.valueOf(Type.getType(Value.class)).repeat(sam.getParameterCount()),
                Type.getType(Value.class)
        );

        implMethod.visitInvokeDynamicInsn(
                "function",
                invokerType,
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        Type.getInternalName(FunctionHooks.class),
                        "callHook",
                        Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class)),
                        false
                )
        );

        genReturnTypeConversion(implMethod, sam);
        implMethod.visitInsn(Type.getType(sam.getReturnType()).getOpcode(Opcodes.IRETURN));
        implMethod.visitMaxs(0, 0);
        implMethod.visitEnd();
    }

    private static void genReturnTypeConversion(MethodVisitor implMethod, Method sam) {
        var returnType = sam.getReturnType();

        if (returnType == void.class) {
            implMethod.visitInsn(Opcodes.POP);
            return;
        }

        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                implMethod.visitLdcInsn(Type.getType(Boolean.class));
            } else if (returnType == int.class) {
                implMethod.visitLdcInsn(Type.getType(Integer.class));
            } else if (returnType == long.class) {
                implMethod.visitLdcInsn(Type.getType(Long.class));
            } else if (returnType == double.class) {
                implMethod.visitLdcInsn(Type.getType(Double.class));
            } else if (returnType == float.class) {
                implMethod.visitLdcInsn(Type.getType(Float.class));
            } else if (returnType == byte.class) {
                implMethod.visitLdcInsn(Type.getType(Byte.class));
            } else if (returnType == short.class) {
                implMethod.visitLdcInsn(Type.getType(Short.class));
            } else if (returnType == char.class) {
                implMethod.visitLdcInsn(Type.getType(Character.class));
            }
        } else {
            implMethod.visitLdcInsn(Type.getType(returnType));
        }

        implMethod.visitVarInsn(Opcodes.ALOAD, 0);
        implMethod.visitFieldInsn(Opcodes.GETFIELD, CLASS_NAME, "context", Type.getDescriptor(EvaluationContext.class));
        implMethod.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                Type.getInternalName(JavaValueUtil.class),
                "valueToObject",
                Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Value.class), Type.getType(Class.class), Type.getType(EvaluationContext.class)),
                false
        );
        if (returnType.isPrimitive()) {
            if (returnType == boolean.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Boolean.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Boolean.class), "booleanValue", "()Z", false);
            } else if (returnType == int.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Integer.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Integer.class), "intValue", "()I", false);
            } else if (returnType == long.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Long.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Long.class), "longValue", "()J", false);
            } else if (returnType == double.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Double.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Double.class), "doubleValue", "()D", false);
            } else if (returnType == float.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Float.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Float.class), "floatValue", "()F", false);
            } else if (returnType == byte.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Byte.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Byte.class), "byteValue", "()B", false);
            } else if (returnType == short.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Short.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Short.class), "shortValue", "()S", false);
            } else if (returnType == char.class) {
                implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(Character.class));
                implMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(Character.class), "charValue", "()C", false);
            }
        } else {
            implMethod.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
        }
    }

    private static void genConstructor(ClassWriter writer) {
        var constructor = writer.visitMethod(
                0,
                "<init>",
                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Value.FunctionValue.class), Type.getType(EvaluationContext.class)),
                null,
                null
        );
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 1);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, CLASS_NAME, "impl", Type.getDescriptor(Value.FunctionValue.class));
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitVarInsn(Opcodes.ALOAD, 2);
        constructor.visitFieldInsn(Opcodes.PUTFIELD, CLASS_NAME, "context", Type.getDescriptor(EvaluationContext.class));
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
    }

    @SuppressWarnings("unchecked")
    private static <T> T getFromCache(Class<T> interfaceClass, EvaluationContext context, Value.FunctionValue function) {
        return (T) CACHE.get(interfaceClass).generate(context, function);
    }

    private Object generate(EvaluationContext context, Value.FunctionValue function) {
        try {
            return constructor.invoke(function, context);
        } catch (Throwable e) {
            throw new IllegalStateException("Error while constructing lambda bridge", e);
        }
    }
}

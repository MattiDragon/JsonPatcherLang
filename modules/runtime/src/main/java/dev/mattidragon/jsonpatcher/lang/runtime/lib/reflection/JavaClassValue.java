package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.JavaValueUtil.ClassChild.ConstructorChild;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.JavaValueUtil.ClassChild.FieldChild;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.JavaValueUtil.ClassChild.InnerClass;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.JavaValueUtil.ClassChild.MethodChild;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessFlag;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JavaClassValue implements Value.SpecialValue {
    private final Class<?> clazz;
    private final Map<String, Supplier<Value>> propertyGetterCache = new HashMap<>();
    private final Map<String, Consumer<Value>> propertySetterCache = new HashMap<>();
    private final Map<Integer, MethodHandle> constructorsByArgCount = new HashMap<>();

    public JavaClassValue(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Value getProperty(String property, EvaluationContext context) {
        if (property.equals("class")) {
            return new JavaObjectValue(clazz);
        }

        if (propertyGetterCache.containsKey(property)) {
            return propertyGetterCache.get(property).get();
        }

        Supplier<Value> supplier = switch (JavaValueUtil.resolveClassChild(clazz, property)) {
            case ConstructorChild(var constructor) -> {
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.LOOKUP.unreflectConstructor(constructor);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public constructor", e);
                }
                var value = new JavaMethodValue(handle);
                yield () -> value;
            }
            case FieldChild(var field) -> {
                if (!field.accessFlags().contains(AccessFlag.STATIC)) {
                    throw context.createException("Cannot reference instance field on class");
                }
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectGetter(field), context);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public field", e);
                }
                yield () -> {
                    try {
                        return (Value) handle.invoke();
                    } catch (Error | RuntimeException e) {
                        throw e;
                    } catch (Throwable e) {
                        throw new RuntimeException("Unexpected error while reading field", e);
                    }
                };
            }
            case MethodChild(var method) -> {
                if (!method.accessFlags().contains(AccessFlag.STATIC)) {
                    throw context.createException("Cannot reference instance method on class");
                }
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.LOOKUP.unreflect(PublicSuperUtil.findAccessibleSuper(method));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public method", e);
                }
                var value = new JavaMethodValue(handle);
                yield () -> value;
            }
            case InnerClass(var innerClass) -> {
                var value = new JavaClassValue(innerClass);
                yield () -> value;
            }
        };

        propertyGetterCache.put(property, supplier);
        return supplier.get();
    }

    @Override
    public void setProperty(String property, Value value, EvaluationContext context) {
        if (propertySetterCache.containsKey(property)) {
            propertySetterCache.get(property).accept(value);
            return;
        }

        var classChild = JavaValueUtil.resolveClassChild(clazz, property);

        if (!(classChild instanceof FieldChild(var field))) {
            throw context.createException("Can only set fields on java objects (tried to set " + property + ")");
        }
        if (!field.accessFlags().contains(AccessFlag.STATIC)) {
            throw context.createException("Cannot reference instance field on class");
        }
        MethodHandle handle;
        try {
            handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectSetter(field), context);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access public field", e);
        }

        Consumer<Value> consumer = v -> {
            try {
                handle.invoke(v);
            } catch (Error | RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException("Unexpected error while writing field", e);
            }
        };

        propertySetterCache.put(property, consumer);
        consumer.accept(value);
    }

    @Override
    public Value invoke(EvaluationContext context, Value... args) {
        var argCount = args.length;

        if (constructorsByArgCount.containsKey(argCount)) {
            try {
                return (Value) constructorsByArgCount.get(argCount).invokeWithArguments((Object[]) args);
            } catch (Error | RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException("Unexpected error while calling constructor", e);
            }
        }

        var available = Arrays.stream(clazz.getConstructors())
                .filter(c -> c.getParameterCount() == argCount)
                .toList();

        if (available.size() > 1) {
            throw new IllegalStateException("Multiple constructors with %s arguments, please call via <init> method".formatted(argCount));
        }
        if (available.isEmpty()) {
            throw new NoSuchElementException("No constructor with %s arguments".formatted(argCount));
        }

        MethodHandle handle;
        try {
            handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectConstructor(available.getFirst()), context);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access public field", e);
        }

        constructorsByArgCount.put(argCount, handle);

        try {
            return (Value) handle.invokeWithArguments((Object[]) args);
        } catch (Error | RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException("Unexpected error while calling constructor", e);
        }
    }

    public Class<?> clazz() {
        return clazz;
    }

    @Override
    public String toString() {
        return "JavaClassValue[" + clazz.getCanonicalName() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JavaClassValue other && other.clazz == clazz;
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }
}

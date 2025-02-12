package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks.FunctionHooks;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.reflection.JavaValueUtil.ClassChild.ConstructorChild;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.reflection.JavaValueUtil.ClassChild.FieldChild;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.reflection.JavaValueUtil.ClassChild.InnerClass;
import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.reflection.JavaValueUtil.ClassChild.MethodChild;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessFlag;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JavaClassValue implements Value.SpecialValue {
    private final Class<?> clazz;
    private final Map<String, Supplier<Value>> propertyGetterCache = new HashMap<>();
    private final Map<String, Consumer<Value>> propertySetterCache = new HashMap<>();

    public JavaClassValue(Class<?> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Value getProperty(String property, PlatformContext context) {
        if (property.equals("class")) {
            return new JavaObjectValue(clazz);
        }

        if (propertyGetterCache.containsKey(property)) {
            return propertyGetterCache.get(property).get();
        }

        Supplier<Value> supplier = switch (JavaValueUtil.resolveClassChild(clazz, property, context)) {
            case ConstructorChild(var constructor) -> {
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectConstructor(constructor));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public constructor", e);
                }
                var argCount = handle.type().parameterCount();
                var value = new FunctionValue(new FunctionHooks.DefinedFunction(handle, argCount, argCount, false));
                yield () -> value;
            }
            case FieldChild(var field) -> {
                if (!field.accessFlags().contains(AccessFlag.STATIC)) {
                    throw context.createException("Cannot reference instance field on class");
                }
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectGetter(field));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public field", e);
                }
                yield () -> {
                    try {
                        return (Value) handle.invoke();
                    } catch (Exception e) {
                        throw context.createException("Error while reading field", e);
                    } catch (Error e) {
                        throw e; // Let it through, we probably shouldn't recover
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
                    handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflect(method));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public method", e);
                }
                var argCount = handle.type().parameterCount();
                var value = new FunctionValue(new FunctionHooks.DefinedFunction(handle, argCount, argCount, false));
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
    public void setProperty(String property, Value value, PlatformContext context) {
        if (propertySetterCache.containsKey(property)) {
            propertySetterCache.get(property).accept(value);
            return;
        }

        var classChild = JavaValueUtil.resolveClassChild(clazz, property, context);

        if (!(classChild instanceof FieldChild(var field))) {
            throw context.createException("Can only set fields on java objects (tried to set " + property + ")");
        }
        if (!field.accessFlags().contains(AccessFlag.STATIC)) {
            throw context.createException("Cannot reference instance field on class");
        }
        MethodHandle handle;
        try {
            handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectSetter(field));
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access public field", e);
        }

        Consumer<Value> consumer = v -> {
            try {
                handle.invoke(v);
            } catch (Exception e) {
                throw context.createException("Error while writing field", e);
            } catch (Error e) {
                throw e; // Let it through, we probably shouldn't recover
            } catch (Throwable e) {
                throw new RuntimeException("Unexpected error while writing field", e);
            }
        };

        propertySetterCache.put(property, consumer);
        consumer.accept(value);
    }

    public Class<?> clazz() {
        return clazz;
    }

    @Override
    public String toString() {
        return "JavaClassValue[" + clazz.getCanonicalName() + "]";
    }
}

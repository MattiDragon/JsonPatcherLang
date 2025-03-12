package dev.mattidragon.jsonpatcher.lang.runtime.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JavaObjectValue implements Value.SpecialValue {
    private final Object object;
    private final Map<String, Supplier<Value>> propertyGetterCache = new HashMap<>();
    private final Map<String, Consumer<Value>> propertySetterCache = new HashMap<>();

    public JavaObjectValue(Object object) {
        this.object = object;
    }

    @Override
    public Value getProperty(String property, PlatformContext context) {
        if (object.getClass().isArray() && property.equals("length")) {
            return new NumberValue(Array.getLength(object));
        }

        if (propertyGetterCache.containsKey(property)) {
            return propertyGetterCache.get(property).get();
        }

        Supplier<Value> supplier = switch (JavaValueUtil.resolveClassChild(object.getClass(), property, context)) {
            case JavaValueUtil.ClassChild.ConstructorChild(var constructor) -> throw context.createException("Cannot use constructor on existing instances");
            case JavaValueUtil.ClassChild.InnerClass(var innerClass) -> throw context.createException("Cannot access inner classes of instances");
            case JavaValueUtil.ClassChild.FieldChild(var field) -> {
                if (field.accessFlags().contains(AccessFlag.STATIC)) {
                    throw context.createException("Cannot reference static field on instance");
                }
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectGetter(field).bindTo(object));
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
            case JavaValueUtil.ClassChild.MethodChild(var method) -> {
                if (method.accessFlags().contains(AccessFlag.STATIC)) {
                    throw context.createException("Cannot reference static method on instance");
                }
                MethodHandle handle;
                try {
                    handle = JavaValueUtil.LOOKUP.unreflect(method).bindTo(object);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access public method", e);
                }
                var value = new JavaMethodValue(handle);
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

        var classChild = JavaValueUtil.resolveClassChild(object.getClass(), property, context);

        if (!(classChild instanceof JavaValueUtil.ClassChild.FieldChild(var field))) {
            throw context.createException("Can only set fields on java objects (tried to set " + property + ")");
        }
        if (field.accessFlags().contains(AccessFlag.STATIC)) {
            throw context.createException("Cannot reference static field on object");
        }
        MethodHandle handle;
        try {
            handle = JavaValueUtil.wrapMethodHandle(JavaValueUtil.LOOKUP.unreflectSetter(field).bindTo(object));
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

    @Override
    public Value get(Value index, PlatformContext context) {
        if (!(object.getClass().isArray())) {
            throw context.createException("Can only index arrays, not arbitrary objects (" + object + ")");
        }
        if (!(index instanceof NumberValue(var number))) {
            throw context.createException("Array index must be number, was " + index);
        }

        return JavaValueUtil.objectToValue(Array.get(object, (int) number));
    }

    @Override
    public void set(Value index, Value value, PlatformContext context) {
        if (!(object.getClass().isArray())) {
            throw context.createException("Can only index arrays, not arbitrary objects (" + object + ")");
        }
        if (!(index instanceof NumberValue(var number))) {
            throw context.createException("Array index must be number, was " + index);
        }

        Array.set(object, (int) number, JavaValueUtil.valueToObject(value, object.getClass().getComponentType()));
    }

    public Object object() {
        return object;
    }

    @Override
    public String toString() {
        return "JavaObjectValue[" + object + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof JavaObjectValue other && other.object == object;
    }

    @Override
    public int hashCode() {
        return object.hashCode();
    }
}

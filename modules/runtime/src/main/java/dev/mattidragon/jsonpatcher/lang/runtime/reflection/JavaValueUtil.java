package dev.mattidragon.jsonpatcher.lang.runtime.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class JavaValueUtil {
    @Language("RegExp")
    private static final String FIELD_DESCRIPTOR = "\\[*(?:[BCDFIJSZ]|L[^.;() ]+;)";
    @Language("RegExp")
    private static final String METHOD_DESCRIPTOR = "\\((?:" + FIELD_DESCRIPTOR + ")*\\)(?:" + FIELD_DESCRIPTOR + "|V)";

    private static final Pattern FIELD_REFERENCE = Pattern.compile("(" + FIELD_DESCRIPTOR + ") ([^.;() ]+)");
    private static final Pattern CONSTRUCTOR_REFERENCE = Pattern.compile("<init>(" + METHOD_DESCRIPTOR + ")");
    private static final Pattern METHOD_REFERENCE = Pattern.compile("([^.;() ]+)(" + METHOD_DESCRIPTOR + ")");
    private static final Pattern SIMPLE_REFERENCE = Pattern.compile("[^.;() ]+");

    // Create a lookup on our classloader, but without any special permissions
    static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup().dropLookupMode(MethodHandles.Lookup.MODULE);
    private static final MethodHandles.Lookup PRIVATE_LOOKUP = MethodHandles.lookup();

    private static final MethodHandle OBJECT_TO_VALUE_HANDLE;
    private static final MethodHandle OBJECT_VALUE_CONSTRUCTOR_HANDLE;
    private static final MethodHandle VALUE_TO_OBJECT_HANDLE;

    static {
        try {
            OBJECT_TO_VALUE_HANDLE = PRIVATE_LOOKUP.findStatic(JavaValueUtil.class, "objectToValue", MethodType.methodType(Value.class, Object.class));
            OBJECT_VALUE_CONSTRUCTOR_HANDLE = PRIVATE_LOOKUP.findConstructor(JavaObjectValue.class, MethodType.methodType(void.class, Object.class));
            VALUE_TO_OBJECT_HANDLE = PRIVATE_LOOKUP.findStatic(JavaValueUtil.class, "valueToObject", MethodType.methodType(Object.class, Value.class, Class.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot find required methods", e);
        }
    }

    public static ClassChild resolveClassChild(Class<?> clazz, String name, PlatformContext context) {
        if (SIMPLE_REFERENCE.matcher(name).matches()) return resolveSimple(clazz, name, context);

        var constructorMatcher = CONSTRUCTOR_REFERENCE.matcher(name);
        if (constructorMatcher.matches()) return resolveConstructor(clazz, name, constructorMatcher, context);

        var methodMatcher = METHOD_REFERENCE.matcher(name);
        if (methodMatcher.matches()) return resolveMethod(clazz, name, methodMatcher, context);

        var fieldMatcher = FIELD_REFERENCE.matcher(name);
        if (fieldMatcher.matches()) return resolveField(clazz, name, fieldMatcher, context);

        throw context.createException("Invalid java member reference");
    }

    private static ClassChild resolveSimple(Class<?> clazz, String name, PlatformContext context) {
        var children = new ArrayList<ClassChild>();
        Arrays.stream(clazz.getMethods())
                .filter(m -> m.getName().equals(name))
                .map(ClassChild.MethodChild::new)
                .forEach(children::add);

        Arrays.stream(clazz.getFields())
                .filter(f -> f.getName().equals(name))
                .map(ClassChild.FieldChild::new)
                .forEach(children::add);

        Arrays.stream(clazz.getClasses())
                .filter(c -> c.getName().equals(name))
                .map(ClassChild.InnerClass::new)
                .forEach(children::add);

        if (children.isEmpty()) {
            throw context.createException("Cannot find public member by the name '" + name + "'");
        } else if (children.size() > 1) {
            throw context.createException("Multiple public members with the name '" + name + "' found, please specify descriptor:\n"
                + children.stream().map(ClassChild::toString).collect(Collectors.joining("\n")));
        }

        return children.getFirst();
    }

    private static ClassChild resolveField(Class<?> clazz, String fullName, Matcher fieldMatcher, PlatformContext context) {
        var desc = fieldMatcher.group(1);
        var name = fieldMatcher.group(2);

        var type = resolveFieldDesc(desc, context);
        for (var field : clazz.getFields()) {
            if (field.getName().equals(name) && field.getType() == type) {
                return new ClassChild.FieldChild(field);
            }
        }

        throw context.createException("Cannot find field " + fullName);
    }

    private static ClassChild resolveConstructor(Class<?> clazz, String fullName, Matcher matcher, PlatformContext context) {
        var desc = matcher.group(1);

        var methodType = MethodType.fromMethodDescriptorString(desc, JavaValueUtil.class.getClassLoader());
        for (var constructor : clazz.getConstructors()) {
            if (Arrays.equals(constructor.getParameterTypes(), methodType.parameterArray())) {
                return new ClassChild.ConstructorChild(constructor);
            }
        }

        throw context.createException("Cannot find constructor " + fullName);
    }

    private static ClassChild resolveMethod(Class<?> clazz, String fullName, Matcher matcher, PlatformContext context) {
        var name = matcher.group(1);
        var desc = matcher.group(2);

        var methodType = MethodType.fromMethodDescriptorString(desc, JavaValueUtil.class.getClassLoader());
        for (var method : clazz.getMethods()) {
            if (method.getName().equals(name)
                && method.getReturnType() == methodType.returnType()
                && Arrays.equals(method.getParameterTypes(), methodType.parameterArray())) {
                return new ClassChild.MethodChild(method);
            }
        }

        throw context.createException("Cannot find method " + fullName);
    }

    private static Class<?> resolveFieldDesc(String desc, PlatformContext context) {
        try {
            return ClassDesc.ofDescriptor(desc).resolveConstantDesc(LOOKUP);
        } catch (ReflectiveOperationException e) {
            throw context.createException("Failed to resolve field descriptor " + desc, e);
        }
    }

    /**
     * Wraps a method handle to jsonpatcher values.
     * @param original The method handle to wrap, can be anything.
     * @return A method handle that only deals in {@link Value}s
     * @see #wrapMethodHandleWeakly(MethodHandle)
     */
    public static MethodHandle wrapMethodHandle(MethodHandle original) {
        var withArgsModified = wrapMethodHandleArgs(original);
        if (original.type().returnType() == void.class) {
            return MethodHandles.filterReturnValue(withArgsModified, MethodHandles.constant(Value.NullValue.class, Value.NullValue.NULL));
        } else {
            return MethodHandles.filterReturnValue(withArgsModified, OBJECT_TO_VALUE_HANDLE.asType(MethodType.methodType(Value.class, original.type().returnType())));
        }
    }

    /**
     * Wraps a method handle to jsonpatcher values. Unlike {@link #wrapMethodHandle(MethodHandle) wrapMethodHandle}
     * the handle returned from here always yields {@link JavaObjectValue}s
     * @param original The method handle to wrap, can be anything.
     * @return A method handle that only deals in {@link Value}s
     * @see #wrapMethodHandle(MethodHandle)
     */
    public static MethodHandle wrapMethodHandleWeakly(MethodHandle original) {
        var withArgsModified = wrapMethodHandleArgs(original);
        if (original.type().returnType() == void.class) {
            return MethodHandles.filterReturnValue(withArgsModified, MethodHandles.constant(Value.NullValue.class, Value.NullValue.NULL));
        } else {
            return MethodHandles.filterReturnValue(withArgsModified, OBJECT_VALUE_CONSTRUCTOR_HANDLE.asType(MethodType.methodType(Value.class, original.type().returnType())));
        }
    }

    private static MethodHandle wrapMethodHandleArgs(MethodHandle original) {
        var originalType = original.type();
        var argCount = originalType.parameterCount();
        var filterArray = new MethodHandle[argCount];
        for (var i = 0; i < argCount; i++) {
            var expectedType = originalType.parameterType(i);
            filterArray[i] = MethodHandles.insertArguments(VALUE_TO_OBJECT_HANDLE, 1, expectedType)
                    .asType(MethodType.methodType(expectedType, Object.class));
        }
        return MethodHandles.filterArguments(original, 0, filterArray);
    }

    /**
     * Converts a java object to a value. If no conversion is available it is wrapped using {@link JavaObjectValue}
     * @param object The object to convert
     * @return The converted value
     */
    public static Value objectToValue(@Nullable Object object) {
        return switch (object) {
            case Value value -> value;
            case String s -> new Value.StringValue(s);
            case Character c -> new Value.StringValue(String.valueOf(c));
            case Number n -> new Value.NumberValue(n.doubleValue());
            case Boolean b -> Value.BooleanValue.of(b);
            case null -> Value.NullValue.NULL;
            default -> new JavaObjectValue(object);
        };
    }

    /**
     * Converts a value to a java object. May fail if no conversion is available.
     * @param value The value to convert
     * @param clazz A class object representing the target type
     * @return The converted value
     * @throws ClassCastException If no conversion is possible.
     * @param <T> The type to convert to
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T valueToObject(Value value, Class<T> clazz) {
        if (Value.class.isAssignableFrom(clazz)) {
            if (clazz.isAssignableFrom(value.getClass())) {
                return clazz.cast(value);
            } else {
                throw new ClassCastException(value + " cannot be cast to " + clazz.getSimpleName());
            }
        }

        if (value instanceof JavaObjectValue objectValue) {
            return clazz.cast(objectValue.object());
        }

        return switch (value) {
            case Value.StringValue(var s) when clazz.isAssignableFrom(String.class) -> clazz.cast(s);
            case Value.BooleanValue booleanValue when clazz == boolean.class || clazz == Boolean.class -> clazz.cast(booleanValue.value());
            case Value.NullValue nullValue when !clazz.isPrimitive() -> null;

            // We need to do unchecked casts as primitive classes don't support casts from wrappers :annoyed:
            case Value.NumberValue(var n) when clazz == long.class || clazz == Long.class -> (T) (Long) (long) n;
            case Value.NumberValue(var n) when clazz == int.class || clazz == Integer.class -> (T) (Integer) (int) n;
            case Value.NumberValue(var n) when clazz == short.class || clazz == Short.class -> (T) (Short) (short) n;
            case Value.NumberValue(var n) when clazz == byte.class || clazz == Byte.class -> (T) (Byte) (byte) n;
            case Value.NumberValue(var n) when clazz == char.class || clazz == Character.class -> (T) (Character) (char) n;
            case Value.StringValue(var s) when clazz == char.class || clazz == Character.class -> {
                if (s.length() != 1) throw new ClassCastException("Only strings of length 1 can be converted to chars");
                yield (T) (Character) s.charAt(0);
            }
            case Value.NumberValue(var n) when clazz == float.class || clazz == Float.class -> (T) (Float) (float) n;
            case Value.NumberValue(var n) when clazz == double.class || clazz == Double.class -> (T) (Double) n;

            default -> throw new ClassCastException(value + " cannot be cast to " + clazz.getSimpleName());
        };
    }

    sealed interface ClassChild {
        record InnerClass(Class<?> clazz) implements ClassChild {
            @Override
            public String toString() {
                return "Inner class '%s'".formatted(clazz.getSimpleName());
            }
        }

        record MethodChild(Method method) implements ClassChild {
            @Override
            public String toString() {
                return "Method '%s(%s)%s'".formatted(
                        method.getName(),
                        Arrays.stream(method.getParameterTypes()).map(Class::descriptorString).collect(Collectors.joining()),
                        method.getReturnType().descriptorString()
                );
            }
        }

        record FieldChild(Field field) implements ClassChild {
            @Override
            public String toString() {
                return "Field '%s %s'".formatted(
                        field.getType().descriptorString(),
                        field.getName()
                );
            }
        }

        record ConstructorChild(Constructor<?> constructor) implements ClassChild {
            @Override
            public String toString() {
                return "Constructor '<init>(%s)V'".formatted(
                        Arrays.stream(constructor.getParameterTypes()).map(Class::descriptorString).collect(Collectors.joining())
                );
            }
        }
    }
}

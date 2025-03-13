package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.PatchException;
import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.remap.Remapper;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;

import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
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

    // Global cache of class child lookups. Use weak keys in case someone loads classes that end up unloading
    private static final Map<Class<?>, Map<String, ClassChild>> CACHE = new WeakHashMap<>();

    static {
        try {
            OBJECT_TO_VALUE_HANDLE = PRIVATE_LOOKUP.findStatic(JavaValueUtil.class, "objectToValue", MethodType.methodType(Value.class, Object.class));
            OBJECT_VALUE_CONSTRUCTOR_HANDLE = PRIVATE_LOOKUP.findConstructor(JavaObjectValue.class, MethodType.methodType(void.class, Object.class));
            VALUE_TO_OBJECT_HANDLE = PRIVATE_LOOKUP.findStatic(JavaValueUtil.class, "valueToObject", MethodType.methodType(Object.class, Value.class, Class.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot find required methods", e);
        }
    }

    static ClassChild resolveClassChild(Class<?> clazz, String name) {
        var map = CACHE.computeIfAbsent(clazz, c -> new HashMap<>());
        if (map.containsKey(name)) {
            return map.get(name);
        }

        if (SIMPLE_REFERENCE.matcher(name).matches()) {
            var child = resolveSimple(clazz, name);
            map.put(name, child);
            return child;
        }

        var constructorMatcher = CONSTRUCTOR_REFERENCE.matcher(name);
        if (constructorMatcher.matches()) {
            var child = resolveConstructor(clazz, name, constructorMatcher);
            map.put(name, child);
            return child;
        }

        var methodMatcher = METHOD_REFERENCE.matcher(name);
        if (methodMatcher.matches()) {
            var child = resolveMethod(clazz, name, methodMatcher);
            map.put(name, child);
            return child;
        }

        var fieldMatcher = FIELD_REFERENCE.matcher(name);
        if (fieldMatcher.matches()) {
            var child = resolveField(clazz, name, fieldMatcher);
            map.put(name, child);
            return child;
        }

        throw new PatchException("Invalid java member reference");
    }

    private static ClassChild resolveSimple(Class<?> clazz, String name) {
        var className = clazz.getName();

        var children = new ArrayList<ClassChild>();
        for (Method method : clazz.getMethods()) {
            var runtimeName = Remapper.COMBINED.remapMethodToRuntime(
                    Remapper.COMBINED.remapClassToNamed(clazz.getName()),
                    name,
                    Remapper.COMBINED.remapMethodDescToNamed(getMethodDesc(method))
            );
            if (method.getName().equals(runtimeName)) {
                ClassChild.MethodChild methodChild = new ClassChild.MethodChild(method);
                children.add(methodChild);
            }
        }

        for (Field field : clazz.getFields()) {
            var runtimeName = Remapper.COMBINED.remapFieldToRuntime(
                    Remapper.COMBINED.remapClassToNamed(className),
                    name,
                    Remapper.COMBINED.remapFieldDescToNamed(field.getType().descriptorString())
            );
            if (field.getName().equals(runtimeName)) {
                ClassChild.FieldChild fieldChild = new ClassChild.FieldChild(field);
                children.add(fieldChild);
            }
        }

        // We remap the full binary name of the inner class, but we select it by the last part.
        // The last part should usually be separated by a $, but some mappings might not respect inner classes,
        // and thus we also check for packages.
        for (Class<?> aClass : clazz.getClasses()) {
            var remappedName = Remapper.COMBINED.remapClassToNamed(aClass.getName());
            // We remap the full binary name of the inner class, but we select it by the last part.
            // The last part should usually be separated by a $, but some mappings might not respect inner classes,
            // and thus we also check for packages.
            var innerName = remappedName.substring(Math.max(remappedName.lastIndexOf('/'), remappedName.lastIndexOf('$')));
            if (innerName.equals(name)) {
                ClassChild.InnerClass innerClass = new ClassChild.InnerClass(aClass);
                children.add(innerClass);
            }
        }

        if (children.isEmpty()) {
            throw new PatchException("Cannot find public member by the name '" + name + "'");
        } else if (children.size() > 1) {
            throw new PatchException("Multiple public members with the name '" + name + "' found, please specify descriptor:\n"
                + children.stream().map(ClassChild::toString).collect(Collectors.joining("\n")));
        }

        return children.getFirst();
    }

    private static String getMethodDesc(Executable m) {
        var returnType = switch (m) {
            case Constructor<?> c -> void.class;
            case Method method -> method.getReturnType();
        };
        StringBuilder builder = new StringBuilder();
        builder.append("(");
        for (var type : m.getParameterTypes()) {
            builder.append(type.descriptorString());
        }
        builder.append(")");
        builder.append(returnType.descriptorString());
        return builder.toString();
    }

    private static ClassChild resolveField(Class<?> clazz, String fullName, Matcher fieldMatcher) {
        var desc = fieldMatcher.group(1);
        var name = fieldMatcher.group(2);

        name = Remapper.COMBINED.remapFieldToRuntime(
                Remapper.COMBINED.remapClassToNamed(clazz.getName()),
                name,
                desc
        );
        desc = Remapper.COMBINED.remapFieldDescToRuntime(desc);

        var type = resolveFieldDesc(desc);
        for (var field : clazz.getFields()) {
            if (field.getName().equals(name) && field.getType() == type) {
                return new ClassChild.FieldChild(field);
            }
        }

        throw new PatchException("Cannot find field " + fullName);
    }

    private static ClassChild resolveConstructor(Class<?> clazz, String fullName, Matcher matcher) {
        var desc = matcher.group(1);

        desc = Remapper.COMBINED.remapMethodDescToRuntime(desc);

        var methodType = MethodType.fromMethodDescriptorString(desc, JavaValueUtil.class.getClassLoader());
        for (var constructor : clazz.getConstructors()) {
            if (Arrays.equals(constructor.getParameterTypes(), methodType.parameterArray())) {
                return new ClassChild.ConstructorChild(constructor);
            }
        }

        throw new PatchException("Cannot find constructor " + fullName);
    }

    private static ClassChild resolveMethod(Class<?> clazz, String fullName, Matcher matcher) {
        var name = matcher.group(1);
        var desc = matcher.group(2);

        name = Remapper.COMBINED.remapMethodToRuntime(
                Remapper.COMBINED.remapClassToNamed(clazz.getName()),
                name,
                desc
        );
        desc = Remapper.COMBINED.remapMethodDescToRuntime(desc);

        var methodType = MethodType.fromMethodDescriptorString(desc, JavaValueUtil.class.getClassLoader());
        for (var method : clazz.getMethods()) {
            if (method.getName().equals(name)
                && method.getReturnType() == methodType.returnType()
                && Arrays.equals(method.getParameterTypes(), methodType.parameterArray())) {
                return new ClassChild.MethodChild(method);
            }
        }

        throw new PatchException("Cannot find method " + fullName);
    }

    private static Class<?> resolveFieldDesc(String desc) {
        try {
            return ClassDesc.ofDescriptor(desc).resolveConstantDesc(LOOKUP);
        } catch (ReflectiveOperationException e) {
            throw new PatchException("Failed to resolve field descriptor " + desc, e);
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

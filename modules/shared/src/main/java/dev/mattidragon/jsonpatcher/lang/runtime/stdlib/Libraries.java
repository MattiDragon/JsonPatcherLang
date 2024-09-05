package dev.mattidragon.jsonpatcher.lang.runtime.stdlib;

import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Supplier;

/**
 * Contains standard libraries. They are built using reflection over public class members.
 * Private members are ignored and a zero argument public constructor is required.
 * Values of fields are placed directly into the library object, which methods are converted to functions first.
 * Method overloading is supported for differing argument counts only.
 */
@SuppressWarnings("unused")
public class Libraries {
    /**
     * Libraries that can be imported by the user.
     */
    public static final Map<String, Supplier<Value.ObjectValue>> LOOKUP = Map.of();
    /**
     * Libraries that are built in and always available.
     */
    public static final Map<String, Supplier<Value.ObjectValue>> BUILTIN = Map.of(
            "math", new LibraryBuilder(MathLibrary.class)::build,
            "arrays", new LibraryBuilder(ArraysLibrary.class)::build,
            "objects", new LibraryBuilder(ObjectsLibrary.class)::build,
            "strings", new LibraryBuilder(StringsLibrary.class)::build,
            "functions", new LibraryBuilder(FunctionsLibrary.class)::build,
            "debug", new LibraryBuilder(DebugLibrary.class)::build);

    /**
     * Finds properties on values defined by the standard libraries
     * @param value The value to look for the property on
     * @param property The property to look for
     * @return The value of the property or {@code null} if not found
     */
    public static @Nullable Value getProperty(Value value, String property) {
        switch (value) {
            case Value.ArrayValue arrayValue -> {
                if (property.equals("length")) {
                    return new Value.NumberValue(arrayValue.value().size());
                } else if (ArraysLibrary.METHODS.containsKey(property)) {
                    var function = ArraysLibrary.METHODS.get(property);
                    return new Value.FunctionValue(function.bind(arrayValue));
                }
            }
            case Value.StringValue stringValue -> {
                if (StringsLibrary.METHODS.containsKey(property)) {
                    var function = StringsLibrary.METHODS.get(property);
                    return new Value.FunctionValue(function.bind(stringValue));
                }
            }
            case Value.FunctionValue functionValue -> {
                if (FunctionsLibrary.METHODS.containsKey(property)) {
                    var function = FunctionsLibrary.METHODS.get(property);
                    return new Value.FunctionValue(function.bind(functionValue));
                }
            }
            default -> {}
        }
        return null;
    }

    public static class MathLibrary {
        public final Value.NumberValue PI = new Value.NumberValue(Math.PI);
        public final Value.NumberValue E = new Value.NumberValue(Math.E);
        public final Value.NumberValue NaN = new Value.NumberValue(Double.NaN);
        public final Value.NumberValue POSITIVE_INFINITY = new Value.NumberValue(Double.POSITIVE_INFINITY);
        public final Value.NumberValue NEGATIVE_INFINITY = new Value.NumberValue(Double.NEGATIVE_INFINITY);

        // We use fields here because there are many similar methods
        public final Value.FunctionValue asin = numberUnary(Math::asin);
        public final Value.FunctionValue sin = numberUnary(Math::sin);
        public final Value.FunctionValue sinh = numberUnary(Math::sinh);
        public final Value.FunctionValue acos = numberUnary(Math::acos);
        public final Value.FunctionValue cos = numberUnary(Math::cos);
        public final Value.FunctionValue cosh = numberUnary(Math::cosh);
        public final Value.FunctionValue atan = numberUnary(Math::atan);
        public final Value.FunctionValue tan = numberUnary(Math::tan);
        public final Value.FunctionValue tanh = numberUnary(Math::tanh);
        public final Value.FunctionValue exp = numberUnary(Math::exp);
        public final Value.FunctionValue log = numberUnary(Math::log);
        public final Value.FunctionValue log10 = numberUnary(Math::log10);
        public final Value.FunctionValue sqrt = numberUnary(Math::sqrt);
        public final Value.FunctionValue cbrt = numberUnary(Math::cbrt);
        public final Value.FunctionValue ceil = numberUnary(Math::ceil);
        public final Value.FunctionValue floor = numberUnary(Math::floor);
        public final Value.FunctionValue abs = numberUnary(Math::abs);
        public final Value.FunctionValue signum = numberUnary(Math::signum);

        public Value.NumberValue max(Value.NumberValue first, Value.NumberValue second) {
            return new Value.NumberValue(Math.max(first.value(), second.value()));
        }

        public Value.NumberValue min(Value.NumberValue first, Value.NumberValue second) {
            return new Value.NumberValue(Math.min(first.value(), second.value()));
        }

        private static Value.FunctionValue numberUnary(DoubleUnaryOperator operator) {
            return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> {
                if (!(args.getFirst() instanceof Value.NumberValue value)) {
                    throw context.createException("Expected argument to be number, was %s".formatted(args.getFirst()));
                }
                return new Value.NumberValue(operator.applyAsDouble(value.value()));
            }).argCount(1));
        }
    }

    public static class ArraysLibrary {
        @DontBind
        public static final Map<String, PatchFunction.BuiltInPatchFunction> METHODS = new LibraryBuilder(ArraysLibrary.class, Method.class).getFunctions();

        @Method
        public Value.ArrayValue insert(PlatformContext context, Value.ArrayValue array, Value.NumberValue index, Value value) {
            array.value().add(fixIndexForInsert(context, (int) index.value(), array.value().size()), value);
            return array;
        }

        @Method
        public Value.ArrayValue push(Value.ArrayValue array, Value value) {
            array.value().add(value);
            return array;
        }

        @Method
        public Value pop(PlatformContext context, Value.ArrayValue array) {
            if (array.value().isEmpty()) throw context.createException("Can't pop from empty array");
            return array.value().removeLast();
        }

        @Method
        public Value.ArrayValue remove(PlatformContext context, Value.ArrayValue array, Value element) {
            array.value().remove(element);
            return array;
        }

        @Method
        public Value removeAt(PlatformContext context, Value.ArrayValue array, Value.NumberValue index) {
            var found = array.get(index, context);
            array.delete(index, context);
            return found;
        }

        @Method
        public Value.ArrayValue map(PlatformContext context, Value.ArrayValue array, Value.FunctionValue function) {
            var newArray = new Value.ArrayValue();
            for (var value : array.value()) {
                newArray.value().add(context.execute(function.function(), List.of(value)));
            }
            return newArray;
        }

        @Method
        public Value.ArrayValue replace(PlatformContext context, Value.ArrayValue array, Value.FunctionValue function) {
            for (int i = 0; i < array.value().size(); i++) {
                array.value().set(i, context.execute(function.function(), List.of(array.get(new Value.NumberValue(i), context))));
            }
            return array;
        }

        @Method
        public Value.ArrayValue filter(PlatformContext context, Value.ArrayValue array, Value.FunctionValue function) {
            var newArray = new Value.ArrayValue();
            for (var value : array.value()) {
                var result = context.execute(function.function(), List.of(value));
                if (result.asBoolean()) newArray.value().add(value);
            }
            return newArray;
        }

        @Method
        public Value.ArrayValue removeIf(PlatformContext context, Value.ArrayValue array, Value.FunctionValue function) {
            array.value().removeIf(value -> context.execute(function.function(), List.of(value)).asBoolean());
            return array;
        }

        @Method
        public Value reduce(PlatformContext context, Value.ArrayValue array, Value.FunctionValue function, Value initialValue) {
            var result = initialValue;
            for (var value : array.value()) {
                result = context.execute(function.function(), List.of(result, value));
            }
            return result;
        }

        @Method
        public Value.ArrayValue slice(PlatformContext context, Value.ArrayValue array, Value.NumberValue start, Value.NumberValue end) {
            var newArray = new Value.ArrayValue();
            var s = (int) start.value();
            var e = (int) end.value();
            if (s < 0 || s > array.value().size()) throw context.createException("Array index out of bounds (index: %s, size: %s)".formatted(s, array.value().size()));
            if (e < 0 || e > array.value().size()) throw context.createException("Array index out of bounds (index: %s, size: %s)".formatted(e, array.value().size()));
            if (s > e) throw context.createException("Start index must be less than end index (start: %s, end: %s)".formatted(s, e));
            for (int i = s; i < e; i++) {
                newArray.value().add(array.value().get(i));
            }
            return newArray;
        }

        @Method
        public Value.ArrayValue slice(PlatformContext context, Value.ArrayValue array, Value.NumberValue start) {
            var newArray = new Value.ArrayValue();
            var s = (int) start.value();
            if (s < 0 || s > array.value().size()) throw context.createException("Array index out of bounds (index: %s, size: %s)".formatted(s, array.value().size()));
            for (int i = s; i < array.value().size(); i++) {
                newArray.value().add(array.value().get(i));
            }
            return newArray;
        }
        
        @Method
        public Value.NumberValue indexOf(Value.ArrayValue array, Value value) {
            return new Value.NumberValue(array.value().indexOf(value));
        }

        // Can't use same algorithm as normal access, because you can also add at the end.
        @DontBind
        private static int fixIndexForInsert(PlatformContext context, int index, int size) {
            if (index > size || index < -size) {
                throw context.createException("Array index out of bounds (index: %s, size: %s)".formatted(index, size));
            }
            if (index < 0) return size + index + 1; // Offset needed for -1 to mean last element
            return index;
        }
    }
    
    public static class ObjectsLibrary {
        public Value.ArrayValue keys(Value.ObjectValue object) {
            var array = new Value.ArrayValue();
            object.value().keySet().stream().map(Value.StringValue::new).forEach(array.value()::add);
            return array;
        }
    }

    public static class StringsLibrary {
        @DontBind
        public static final Map<String, PatchFunction.BuiltInPatchFunction> METHODS = new LibraryBuilder(StringsLibrary.class, Method.class).getFunctions();

        @Method
        public Value.BooleanValue matches(Value.StringValue string,  Value.StringValue pattern) {
            return Value.BooleanValue.of(string.value().matches(pattern.value()));
        }
        
        @Method
        public Value.StringValue replace(PlatformContext context, Value.StringValue string, Value.StringValue pattern, Value.StringValue replacement) {
            return new Value.StringValue(string.value().replace(pattern.value(), replacement.value()));
        }

        @Method
        public Value.StringValue replaceRegex(PlatformContext context, Value.StringValue string, Value.StringValue pattern, Value.StringValue replacement) {
            return new Value.StringValue(string.value().replaceAll(pattern.value(), replacement.value()));
        }

        @Method
        public Value.ArrayValue split(PlatformContext context, Value.StringValue string, Value.StringValue pattern) {
            var array = new Value.ArrayValue();
            for (var part : string.value().split(pattern.value())) {
                array.value().add(new Value.StringValue(part));
            }
            return array;
        }

        @Method
        public Value.StringValue toLowerCase(PlatformContext context, Value.StringValue string) {
            return new Value.StringValue(string.value().toLowerCase(Locale.ROOT));
        }

        @Method
        public Value.StringValue toUpperCase(PlatformContext context, Value.StringValue string) {
            return new Value.StringValue(string.value().toUpperCase(Locale.ROOT));
        }

        @Method
        public Value.StringValue trim(PlatformContext context, Value.StringValue string) {
            return new Value.StringValue(string.value().strip());
        }

        @Method
        public Value.StringValue trimStart(PlatformContext context, Value.StringValue string) {
            return new Value.StringValue(string.value().stripLeading());
        }

        @Method
        public Value.StringValue trimEnd(PlatformContext context, Value.StringValue string) {
            return new Value.StringValue(string.value().stripTrailing());
        }

        @Method
        public Value.BooleanValue startsWith(PlatformContext context, Value.StringValue string, Value.StringValue prefix) {
            return Value.BooleanValue.of(string.value().startsWith(prefix.value()));
        }

        @Method
        public Value.BooleanValue endsWith(PlatformContext context, Value.StringValue string, Value.StringValue suffix) {
            return Value.BooleanValue.of(string.value().endsWith(suffix.value()));
        }

        @Method
        public Value.BooleanValue contains(PlatformContext context, Value.StringValue string, Value.StringValue substring) {
            return Value.BooleanValue.of(string.value().contains(substring.value()));
        }

        @Method
        public Value.NumberValue length(PlatformContext context, Value.StringValue string) {
            return new Value.NumberValue(string.value().length());
        }

        @Method
        public Value.BooleanValue isEmpty(PlatformContext context, Value.StringValue string) {
            return Value.BooleanValue.of(string.value().isEmpty());
        }

        @Method
        public Value.BooleanValue isBlank(PlatformContext context, Value.StringValue string) {
            return Value.BooleanValue.of(string.value().isBlank());
        }

        @Method
        public Value.StringValue charAt(PlatformContext context, Value.StringValue string, Value.NumberValue index) {
            var i = (int) index.value();
            if (i < 0 || i >= string.value().length()) throw context.createException("String index out of bounds (index: %s, size: %s)".formatted(i, string.value().length()));
            return new Value.StringValue(string.value().substring(i, i + 1));
        }

        @Method
        public Value.ArrayValue chars(PlatformContext context, Value.StringValue string) {
            var array = new Value.ArrayValue();
            for (var c : string.value().toCharArray()) {
                array.value().add(new Value.StringValue(String.valueOf(c)));
            }
            return array;
        }

        @Method
        public Value.StringValue substring(PlatformContext context, Value.StringValue string, Value.NumberValue start, Value.NumberValue end) {
            var s = (int) start.value();
            var e = (int) end.value();
            if (s < 0 || s > string.value().length()) throw context.createException("String index out of bounds (index: %s, size: %s)".formatted(s, string.value().length()));
            if (e < 0 || e > string.value().length()) throw context.createException("String index out of bounds (index: %s, size: %s)".formatted(e, string.value().length()));
            if (s > e) throw context.createException("Start index must be less than end index (start: %s, end: %s)".formatted(s, e));
            return new Value.StringValue(string.value().substring(s, e));
        }

        @Method
        public Value.StringValue substring(PlatformContext context, Value.StringValue string, Value.NumberValue start) {
            var s = (int) start.value();
            if (s < 0 || s > string.value().length()) throw context.createException("String index out of bounds (index: %s, size: %s)".formatted(s, string.value().length()));
            return new Value.StringValue(string.value().substring(s));
        }

        public Value.StringValue join(PlatformContext context, Value.ArrayValue array, Value.StringValue separator) {
            var builder = new StringBuilder();
            var first = true;
            for (var value : array.value()) {
                if (!first) builder.append(separator.value());
                first = false;
                builder.append(asString(context, value));
            }
            return new Value.StringValue(builder.toString());
        }

        public Value asString(PlatformContext context, Value value) {
            if (value instanceof Value.StringValue string) {
                return string;
            }
            if (value instanceof Value.NumberValue number) {
                return new Value.StringValue(String.valueOf(number.value()));
            }
            if (value instanceof Value.BooleanValue bool) {
                return new Value.StringValue(String.valueOf(bool.value()));
            }
            if (value instanceof Value.NullValue) {
                return new Value.StringValue("null");
            }
            if (value instanceof Value.ArrayValue array) {
                var builder = new StringBuilder();
                builder.append('[');
                for (int i = 0; i < array.value().size(); i++) {
                    if (i != 0) builder.append(", ");
                    builder.append(asString(context, array.value().get(i)));
                }
                builder.append(']');
                return new Value.StringValue(builder.toString());
            }
            if (value instanceof Value.ObjectValue object) {
                var builder = new StringBuilder();
                builder.append('{');
                var first = true;
                for (var entry : object.value().entrySet()) {
                    if (!first) builder.append(", ");
                    first = false;
                    builder.append(entry.getKey()).append(": ").append(asString(context, entry.getValue()));
                }
                builder.append('}');
                return new Value.StringValue(builder.toString());
            }
            throw context.createException("Can't convert %s to string".formatted(value));
        }
    }

    public static class FunctionsLibrary {
        @DontBind
        public static final Map<String, PatchFunction.BuiltInPatchFunction> METHODS = new LibraryBuilder(FunctionsLibrary.class, Method.class).getFunctions();

        @Method
        public static Value.FunctionValue bind(Value.FunctionValue function, Value value) {
            return new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (context, args) -> {
                var newArgs = new ArrayList<>(args);
                newArgs.addFirst(value);
                return context.execute(function.function(), newArgs);
            });
        }

        @Method
        public Value.FunctionValue bind(Value.FunctionValue function, Value value, Value.NumberValue index) {
            return new Value.FunctionValue((PatchFunction.BuiltInPatchFunction) (context, args) -> {
                var newArgs = new ArrayList<>(args);
                newArgs.add((int) index.value(), value);
                return context.execute(function.function(), newArgs);
            });
        }

        @Method
        public Value.FunctionValue then(Value.FunctionValue function, Value.FunctionValue next) {
            return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> {
                var result = context.execute(function.function(), args);
                return context.execute(next.function(), List.of(result));
            }));
        }


        public Value.FunctionValue identity() {
            return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> args.getFirst()).argCount(1));
        }

        public Value.FunctionValue constant(Value value) {
            return new Value.FunctionValue(((PatchFunction.BuiltInPatchFunction) (context, args) -> value).argCount(0));
        }
    }

    public static class DebugLibrary {
        public void log(PlatformContext context, Value value) {
            context.log(value);
        }

        @DisableErrorWrapping
        @FunctionName("throw")
        public void throw_(PlatformContext context, Value value) {
            throw context.createException(value.toString());
        }

        @DisableErrorWrapping
        @FunctionName("assert")
        public void assert_(PlatformContext context, Value value) {
            assert_(context, value, new Value.StringValue("Assertion failed"));
        }

        @DisableErrorWrapping
        @FunctionName("assert")
        public void assert_(PlatformContext context, Value value, Value message) {
            if (!value.asBoolean()) throw context.createException(message.toString());
        }
    }
}

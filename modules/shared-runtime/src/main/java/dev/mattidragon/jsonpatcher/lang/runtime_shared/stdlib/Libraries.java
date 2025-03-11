package dev.mattidragon.jsonpatcher.lang.runtime_shared.stdlib;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;

import java.util.Locale;
import java.util.Map;
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
            return switch (value) {
                case Value.StringValue string -> string;
                case Value.NumberValue number -> new Value.StringValue(String.valueOf(number.value()));
                case Value.BooleanValue bool -> new Value.StringValue(String.valueOf(bool.value()));
                case Value.NullValue nullValue -> new Value.StringValue("null");
                case Value.ArrayValue array -> {
                    var builder = new StringBuilder();
                    builder.append('[');
                    for (int i = 0; i < array.value().size(); i++) {
                        if (i != 0) builder.append(", ");
                        builder.append(asString(context, array.value().get(i)));
                    }
                    builder.append(']');
                    yield new Value.StringValue(builder.toString());
                }
                case Value.ObjectValue object -> {
                    var builder = new StringBuilder();
                    builder.append('{');
                    var first = true;
                    for (var entry : object.value().entrySet()) {
                        if (!first) builder.append(", ");
                        first = false;
                        builder.append(entry.getKey()).append(": ").append(asString(context, entry.getValue()));
                    }
                    builder.append('}');
                    yield new Value.StringValue(builder.toString());
                }
                default -> throw context.createException("Can't convert %s to string".formatted(value));
            };
        }
    }
}

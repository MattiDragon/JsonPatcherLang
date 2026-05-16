package dev.mattidragon.jsonpatcher.lang.runtime.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

public class StringHooks {
    private StringHooks() {
    }

    public static String asString(Value value) {
        return switch (value) {
            case Value.BooleanValue.TRUE -> "true";
            case Value.BooleanValue.FALSE -> "false";
            case Value.NullValue.NULL -> "null";
            case Value.NumberValue(var number) -> Double.toString(number);
            case Value.StringValue(var string) -> string;
            case Value.ArrayValue(var children, var frozen) -> {
                var sb = new StringBuilder();
                sb.append('[');
                for (int i = 0; i < children.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(asString(children.get(i)));
                }
                sb.append(']');
                yield sb.toString();
            }
            case Value.ObjectValue(var children, var frozen) -> {
                var sb = new StringBuilder();
                sb.append('{');
                int i = 0;
                for (var entry : children.entrySet()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append(entry.getKey());
                    sb.append(": ");
                    sb.append(asString(entry.getValue()));
                }
                sb.append('}');
                yield sb.toString();
            }

            case Value.FunctionValue functionValue -> functionValue.toString();
            case Value.SpecialValue specialValue -> specialValue.toString();
        };
    }
}

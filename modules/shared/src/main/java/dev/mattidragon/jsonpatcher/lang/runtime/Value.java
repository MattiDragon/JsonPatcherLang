package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionContext;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public sealed interface Value {
    ThreadLocal<Set<Value>> TO_STRING_RECURSION_TRACKER = ThreadLocal.withInitial(HashSet::new);

    static boolean isEqual(Value value1, Value value2) {
        record Pair(Value first, Value second) {}
        
        return switch (new Pair(value1, value2)) {
            case Pair(NumberValue(var first), NumberValue(var second)) -> first == second;
            case Pair(StringValue(var first), StringValue(var second)) -> first.equals(second);
            case Pair(BooleanValue first, BooleanValue second) -> first == second;
            // TODO: fix nested array and object checks
            case Pair(ArrayValue(var first), ArrayValue(var second)) -> first.equals(second);
            case Pair(ObjectValue(var first), ObjectValue(var second)) -> first.equals(second);
            case Pair(FunctionValue(var first), FunctionValue(var second)) -> first.equals(second);
            case Pair(NullValue first, NullValue second) -> true;
            default -> false;
        };
    }

    boolean asBoolean();

    @NotNull
    static Value convertNull(@Nullable Value value) {
        return value == null ? NullValue.NULL : value;
    }

    record ObjectValue(Map<String, Value> value) implements Value {
        public ObjectValue {
            value = new LinkedHashMap<>(value);
        }

        public ObjectValue() {
            this(Map.of());
        }

        public Value get(String key, FunctionContext context) {
            if (!value.containsKey(key)) {
                throw context.createException("Object %s has no key %s".formatted(this, key));
            }
            return value.get(key);
        }

        public void set(String key, Value value, FunctionContext context) {
            this.value.put(key, value);
        }

        public void remove(String key, FunctionContext context) {
            if (!value.containsKey(key)) {
                throw context.createException("Object %s has no key %s".formatted(this, key));
            }
            value.remove(key);
        }

        @Override
        public boolean asBoolean() {
            return !value.isEmpty();
        }

        @Override
        public String toString() {
            if (TO_STRING_RECURSION_TRACKER.get().contains(this)) return "{...}";
            try {
                TO_STRING_RECURSION_TRACKER.get().add(this);
                return value.toString();
            } finally {
                TO_STRING_RECURSION_TRACKER.get().remove(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    record ArrayValue(List<Value> value) implements Value {
        public ArrayValue {
            value = new ArrayList<>(value);
        }

        public ArrayValue() {
            this(List.of());
        }

        public Value get(int index, FunctionContext context) {
            return this.value.get(fixIndex(index, context));
        }

        public void set(int index, Value value, FunctionContext context) {
            this.value.set(fixIndex(index, context), value);
        }

        public void remove(int index, FunctionContext context) {
            value.remove(fixIndex(index, context));
        }

        private int fixIndex(int index, FunctionContext context) {
            if (index >= value.size() || index < -value.size()) {
                throw context.createException("Array index out of bounds (index: %s, size: %s)".formatted(index, value.size()));
            }
            if (index < 0) return value.size() + index;
            return index;
        }

        @Override
        public boolean asBoolean() {
            return !value.isEmpty();
        }

        @Override
        public String toString() {
            if (TO_STRING_RECURSION_TRACKER.get().contains(this)) return "[...]";
            try {
                TO_STRING_RECURSION_TRACKER.get().add(this);
                return value.toString();
            } finally {
                TO_STRING_RECURSION_TRACKER.get().remove(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    record FunctionValue(PatchFunction function) implements Value {
        @Override
        public boolean asBoolean() {
            return true;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }

        @Override
        public String toString() {
            return "<function>";
        }
    }

    sealed interface Primitive extends Value {}

    record StringValue(String value) implements Primitive {
        @Override
        public boolean asBoolean() {
            return !value.isEmpty();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    record NumberValue(double value) implements Primitive {
        @Override
        public boolean asBoolean() {
            return value != 0;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    enum BooleanValue implements Primitive {
        TRUE, FALSE;

        public static BooleanValue of(boolean value) {
            return value ? TRUE : FALSE;
        }

        public boolean value() {
            return this == TRUE;
        }

        @Override
        public boolean asBoolean() {
            return value();
        }

        @Override
        public String toString() {
            return String.valueOf(value());
        }
    }

    enum NullValue implements Primitive {
        NULL;

        @Override
        public boolean asBoolean() {
            return false;
        }

        @Override
        public String toString() {
            return "null";
        }
    }
}

package io.github.mattidragon.jsonpatcher.lang.runtime;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.PatchFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public sealed interface Value {
    ThreadLocal<Set<Value>> TO_STRING_RECURSION_TRACKER = ThreadLocal.withInitial(HashSet::new);

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

        public Value get(String key, LangConfig config, @Nullable SourceSpan pos) {
            if (!value.containsKey(key)) throw new EvaluationException(config, "Object %s has no key %s".formatted(this, key), pos);
            return value.get(key);
        }

        public void set(String key, Value value, LangConfig config, @Nullable SourceSpan pos) {
            this.value.put(key, value);
        }

        public void remove(String key, LangConfig config, SourceSpan pos) {
            if (!value.containsKey(key)) throw new EvaluationException(config, "Object %s has no key %s".formatted(this, key), pos);
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

        public Value get(int index, LangConfig config, @Nullable SourceSpan pos) {
            return this.value.get(fixIndex(index, config, pos));
        }

        public void set(int index, Value value, LangConfig config, @Nullable SourceSpan pos) {
            this.value.set(fixIndex(index, config, pos), value);
        }

        public void remove(int index, LangConfig config, SourceSpan pos) {
            value.remove(fixIndex(index, config, pos));
        }

        private int fixIndex(int index, LangConfig config, @Nullable SourceSpan pos) {
            if (index >= value.size() || index < -value.size())
                throw new EvaluationException(config, "Array index out of bounds (index: %s, size: %s)".formatted(index, value.size()), pos);
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

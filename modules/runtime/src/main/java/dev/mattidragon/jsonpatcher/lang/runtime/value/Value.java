package dev.mattidragon.jsonpatcher.lang.runtime.value;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;

import java.util.*;
import java.util.stream.IntStream;

public sealed interface Value {
    ThreadLocal<Set<Value>> TO_STRING_RECURSION_TRACKER = ThreadLocal.withInitial(HashSet::new);

    static boolean isEqual(Value value1, Value value2) {
        if (value1 == value2) return true;

        record Pair(Value first, Value second) {}
        
        return switch (new Pair(value1, value2)) {
            case Pair(NumberValue(var first), NumberValue(var second)) -> first == second;
            case Pair(StringValue(var first), StringValue(var second)) -> first.equals(second);
            case Pair(BooleanValue first, BooleanValue second) -> first == second;
            case Pair(ArrayValue first, ArrayValue second) ->
                    first.value.size() == second.value.size() && IntStream.range(0, first.value.size())
                            .allMatch(i -> isEqual(first.value.get(i), second.value.get(i)));
            case Pair(ObjectValue first, ObjectValue second) ->
                    first.value.size() == second.value.size() && first.value.keySet().stream()
                            .allMatch(i -> isEqual(first.value.get(i), second.value.get(i)));
            case Pair(FunctionValue(var first), FunctionValue(var second)) -> first.equals(second);
            default -> false;
        };
    }

    @SuppressWarnings("unused")
    boolean asBoolean();
    
    ValueType type();

    @SuppressWarnings("unused")
    default Value get(Value index, EvaluationContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }

    @SuppressWarnings("unused")
    default void set(Value index, Value value, EvaluationContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }

    @SuppressWarnings("unused")
    default void delete(Value index, EvaluationContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }

    @SuppressWarnings("unused")
    default Value getProperty(String property, EvaluationContext context) {
        var libProp = context.getLibraryProperty(this, property);
        if (libProp != null) return libProp;
        
        throw context.createException("Tried to read invalid property %s of %s.".formatted(property, this));
    }

    @SuppressWarnings("unused")
    default void setProperty(String property, Value value, EvaluationContext context) {
        throw context.createException("%s does not have mutable properties (tried to set %s)".formatted(this, property));
    }

    @SuppressWarnings("unused")
    default void deleteProperty(String property, EvaluationContext context) {
        throw context.createException("%s does not have mutable properties (tried to set %s)".formatted(this, property));
    }

    record ObjectValue(Map<String, Value> value, boolean frozen) implements Value {
        public ObjectValue {
            value = frozen ? Collections.unmodifiableMap(new LinkedHashMap<>(value)) : new LinkedHashMap<>(value);
        }

        public ObjectValue(Map<String, Value> value) {
            this(value, false);
        }

        public ObjectValue() {
            this(Map.of());
        }

        @Override
        public Value get(Value index, EvaluationContext context) {
            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            return getProperty(key, context);
        }

        @Override
        public void set(Value index, Value value, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            setProperty(key, value, context);
        }

        @Override
        public void delete(Value index, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            deleteProperty(key, context);
        }

        @Override
        public Value getProperty(String key, EvaluationContext context) {
            if (!value.containsKey(key)) {
                throw context.createException("Object %s has no key %s".formatted(this, key));
            }
            return value.get(key);
        }

        @Override
        public void setProperty(String key, Value value, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

            this.value.put(key, value);
        }

        @Override
        public void deleteProperty(String key, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

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
        public ValueType type() {
            return ValueType.OBJECT;
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

    record ArrayValue(List<Value> value, boolean frozen) implements Value {
        public ArrayValue {
            value = new ArrayList<>(value);
        }

        public ArrayValue(List<Value> value) {
            this(value, false);
        }

        public ArrayValue() {
            this(List.of());
        }

        @Override
        public Value get(Value index, EvaluationContext context) {
            return this.value.get(fixIndex(index, context));
        }

        @Override
        public void set(Value index, Value value, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

            this.value.set(fixIndex(index, context), value);
        }

        @Override
        public void delete(Value index, EvaluationContext context) {
            if (frozen) {
                throw context.createException("Object %s if frozen and cannot be mutated");
            }

            value.remove(fixIndex(index, context));
        }

        private int fixIndex(Value indexValue, EvaluationContext context) {
            if (!(indexValue instanceof NumberValue(var doubleIndex))) {
                throw context.createException("Array %s can only be indexed by number".formatted(this));
            }
            var index = (int) doubleIndex;
            
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
        public ValueType type() {
            return ValueType.ARRAY;
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
        public ValueType type() {
            return ValueType.FUNCTION;
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
        public ValueType type() {
            return ValueType.STRING;
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
        public ValueType type() {
            return ValueType.NUMBER;
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
        public ValueType type() {
            return ValueType.BOOLEAN;
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
        public ValueType type() {
            return ValueType.NULL;
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    non-sealed interface SpecialValue extends Value {
        default Value invoke(EvaluationContext context, Value... args) {
            throw context.createException("Tried to invoke %s, but it can't be invoked".formatted(this));
        }

        @Override
        default boolean asBoolean() {
            return true;
        }

        @Override
        default ValueType type() {
            return ValueType.SPECIAL;
        }

        default Value freeze() {
            return this;
        }
    }
}

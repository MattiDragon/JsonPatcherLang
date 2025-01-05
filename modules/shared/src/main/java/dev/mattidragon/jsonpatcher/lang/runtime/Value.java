package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import org.jspecify.annotations.Nullable;

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
    
    ValueType type();
    
    default Value get(Value index, PlatformContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }
    
    default void set(Value index, Value value, PlatformContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }
    
    default void delete(Value index, PlatformContext context) {
        throw context.createException("Tried to index %s, but it can't be indexed".formatted(this));
    }
    
    default Value getProperty(String property, PlatformContext context) {
        var libProp = context.getLibraryProperty(this, property);
        if (libProp != null) return libProp;
        
        throw context.createException("Tried to read invalid property %s of %s.".formatted(property, this));
    }
    
    default void setProperty(String property, Value value, PlatformContext context) {
        throw context.createException("%s does not have mutable properties (tried to set %s)".formatted(this, property));
    }
    
    default void deleteProperty(String property, PlatformContext context) {
        throw context.createException("%s does not have mutable properties (tried to set %s)".formatted(this, property));
    }

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

        @Override
        public Value get(Value index, PlatformContext context) {
            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            return getProperty(key, context);
        }

        @Override
        public void set(Value index, Value value, PlatformContext context) {
            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            setProperty(key, value, context);
        }

        @Override
        public void delete(Value index, PlatformContext context) {
            if (!(index instanceof StringValue(var key))) {
                throw context.createException("Object %s can only be indexed by string".formatted(this));
            }
            
            deleteProperty(key, context);
        }

        @Override
        public Value getProperty(String key, PlatformContext context) {
            if (!value.containsKey(key)) {
                throw context.createException("Object %s has no key %s".formatted(this, key));
            }
            return value.get(key);
        }

        @Override
        public void setProperty(String key, Value value, PlatformContext context) {
            this.value.put(key, value);
        }

        @Override
        public void deleteProperty(String key, PlatformContext context) {
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

    record ArrayValue(List<Value> value) implements Value {
        public ArrayValue {
            value = new ArrayList<>(value);
        }

        public ArrayValue() {
            this(List.of());
        }

        @Override
        public Value get(Value index, PlatformContext context) {
            return this.value.get(fixIndex(index, context));
        }

        @Override
        public void set(Value index, Value value, PlatformContext context) {
            this.value.set(fixIndex(index, context), value);
        }

        @Override
        public void delete(Value index, PlatformContext context) {
            value.remove(fixIndex(index, context));
        }

        private int fixIndex(Value indexValue, PlatformContext context) {
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
}

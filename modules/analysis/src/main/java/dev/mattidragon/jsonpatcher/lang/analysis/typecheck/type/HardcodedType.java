package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

/**
 * Represents a type with hardcoded special behaviour that cannot be fully represented by the type system.
 * @param base An approximation of this type using regular types
 * @param kind Which specific hardcoded type this is
 */
public record HardcodedType(Type base, Kind kind) implements Type {
    public enum Kind {
        FUNCTION_BIND,
        FUNCTION_CHAIN
    }
}

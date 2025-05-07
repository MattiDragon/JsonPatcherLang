package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

import java.util.Map;
import java.util.Optional;

public record NamedType(
        Type supertype,
        Map<String, Type> properties,
        Optional<Type> wildcardPropertyType,
        Optional<FunctionType> callSignature,
        String name
) implements Type {
    public NamedType {
        properties = Map.copyOf(properties);
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

import java.util.Map;
import java.util.Optional;

public record NamedType(Type supertype, Map<String, Type> properties, Optional<FunctionType> callSignature) implements Type {
    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.v2;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;

import java.util.*;

public class ObjectTypes {
    public static List<ResolvedType> resolveObjectTypes(Type type) {
        return switch (type) {
            case ArrayType arrayType -> List.of();
            case FunctionType functionType -> List.of();
            case HardcodedType hardcodedType -> resolveObjectTypes(hardcodedType.base());
            case LazyType lazyType -> resolveObjectTypes(lazyType.get());
            case NamedType namedType -> List.of(ofNamedType(namedType));
            case ObjectType objectType -> List.of(ofObjectType(objectType));
            case PrimitiveType.OBJECT, SpecialType.UNKNOWN, SpecialType.ANY -> List.of(genericOf(type));
            case PrimitiveType primitiveType -> List.of();
            case SpecialType.NEVER -> List.of();
            case TypeArgument typeArgument -> resolveObjectTypes(typeArgument.bound());
            case UnionType unionType -> unionType.children().stream().map(ObjectTypes::resolveObjectTypes).flatMap(List::stream).toList();
        };
    }

    private static ResolvedType genericOf(Type type) {
        return new ResolvedType(type, Map.of(), Optional.of(SpecialType.UNKNOWN));
    }

    private static ResolvedType ofObjectType(ObjectType objectType) {
        return new ResolvedType(objectType, Map.of(), Optional.of(objectType.component()));
    }

    private static ResolvedType ofNamedType(NamedType namedType) {
        var map = new HashMap<String, ResolvedType.Property>();
        for (var entry : namedType.properties().entrySet()) {
            var prop = entry.getValue();
            map.put(entry.getKey(), new ResolvedType.Property(prop.type(), prop.optional()));
        }

        return new ResolvedType(namedType, Collections.unmodifiableMap(map), Optional.empty());
    }

    public record ResolvedType(
            Type realType,
            Map<String, Property> properties,
            Optional<Type> wildcardPropertyType
    ) {
        public Optional<Property> property(String name) {
            var prop = properties.get(name);
            if (prop != null) {
                return Optional.of(prop);
            } else {
                return wildcardPropertyType.map(type -> new Property(type, true));
            }
        }

        public record Property(Type type, boolean optional) {
        }
    }
}

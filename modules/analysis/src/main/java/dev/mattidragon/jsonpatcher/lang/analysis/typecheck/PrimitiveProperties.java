package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface PrimitiveProperties {
    Map<ValueType, Map<String, Type>> getPrimitivePropertyTypes();

    @Nullable
    default Type getPrimitivePropertyType(ValueType type, String property) {
        var propertyType = getPrimitivePropertyTypes()
                .getOrDefault(type, Map.of())
                .get(property);
        // When in order to bind properly, we have to lose the hardcoded type
        // Doesn't really matter as it won't type check correctly anyway
        if (propertyType instanceof HardcodedType(var base, _)) {
            propertyType = base;
        }
        // Remove self argument from functions
        if (propertyType instanceof FunctionType(var typeArguments, var args, var requiredArgs, var varargs, var returnType)) {
            return new FunctionType(
                    typeArguments,
                    args.stream().skip(1).toList(),
                    Math.max(requiredArgs - 1, 0),
                    varargs,
                    returnType
            );
        }
        return propertyType;
    }

    static @Nullable ValueType convertType(Type type) {
        return switch (type) {
            case PrimitiveType primitiveType -> switch (primitiveType) {
                case OBJECT -> ValueType.OBJECT;
                case SPECIAL -> ValueType.SPECIAL;
                case STRING -> ValueType.STRING;
                case NUMBER -> ValueType.NUMBER;
                case ARRAY -> ValueType.ARRAY;
                case BOOLEAN -> ValueType.BOOLEAN;
                case FUNCTION -> ValueType.FUNCTION;
                case NULL -> ValueType.NULL;
            };
            case NamedType _ -> ValueType.OBJECT;
            case SpecialType _ -> ValueType.SPECIAL;

            case ArrayType _ -> ValueType.ARRAY;
            case FunctionType _ -> ValueType.FUNCTION;
            case LazyType lazyType -> convertType(lazyType.get());
            case ObjectType _ -> ValueType.OBJECT;
            case TypeArgument typeArgument -> convertType(typeArgument.bound());
            case UnionType _ -> null;
            case HardcodedType hardcodedType -> convertType(hardcodedType.base());
        };
    }
}

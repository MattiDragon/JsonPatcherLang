package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;

public class TypeComparison {
    public static boolean isSubtype(Type subType, Type superType) {
        if (subType.equals(superType)) return true;

        switch (superType) {
            case SpecialType.ANY, SpecialType.UNKNOWN -> {
                return true;
            }
            case SpecialType.NEVER -> {
                return false;
            }
            default -> {
            }
        }

        return switch (subType) {
            case SpecialType.NEVER, SpecialType.UNKNOWN -> true;
            case SpecialType.ANY -> false;

            // Specialized types are subtypes of their generic variants
            case ArrayType arrayType when superType == PrimitiveType.ARRAY -> true;
            case FunctionType functionType when superType == PrimitiveType.FUNCTION -> true;
            case ObjectType objectType when superType == PrimitiveType.OBJECT -> true;

            // We treat arrays and maps as covariant, even though they really aren't,
            // because it eliminates the need for usage to impact type
            case ArrayType(var component) when superType instanceof ArrayType(var superComponent) ->
                    isSubtype(component, superComponent);
            case ObjectType(var component) when superType instanceof ObjectType(var superComponent) ->
                    isSubtype(component, superComponent);
            case FunctionType(
                    var typeArguments,
                    var args,
                    var requiredArgs,
                    var varargs,
                    var returnType
            ) when superType instanceof FunctionType(
                    var superTypeArguments,
                    var superArgs,
                    var superRequiredArgs,
                    var superVarargs,
                    var superReturnType
            ) -> {
                // Check if the type arguments are compatible
                if (typeArguments.size() != superTypeArguments.size()) {
                    yield false;
                }
                for (int i = 0; i < typeArguments.size(); i++) {
                    var typeArgument = typeArguments.get(i);
                    var superTypeArgument = superTypeArguments.get(i);

                }

            }

            // If no matching supertype, we fail the check.
            case ArrayType arrayType -> false;
            case ObjectType objectType -> false;
            case FunctionType functionType -> false;

            // Primitive types don't have any valid supertypes except themselves and any
            case PrimitiveType primitiveType -> false;
        };
    }
}

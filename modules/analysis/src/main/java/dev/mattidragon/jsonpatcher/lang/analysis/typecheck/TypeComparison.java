package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;

public class TypeComparison {
    public static boolean isSubtype(Type subType, Type superType) {
        return isSubtype(subType, superType, new TypeArgEqualityStack());
    }

    private static boolean isSubtype(Type subType, Type superType, TypeArgEqualityStack equalTypeArgs) {
        if (subType.equals(superType)) return true;

        switch (superType) {
            case SpecialType.ANY, SpecialType.UNKNOWN -> {
                return true;
            }
            case SpecialType.NEVER -> {
                // Unknown is always valid
                return subType == SpecialType.UNKNOWN;
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
                    isSubtype(component, superComponent, equalTypeArgs);
            case ObjectType(var component) when superType instanceof ObjectType(var superComponent) ->
                    isSubtype(component, superComponent, equalTypeArgs);

            case FunctionType functionType when superType instanceof FunctionType superFunctionType ->
                    isFunctionSubtype(equalTypeArgs, functionType, superFunctionType);

            // If no matching supertype, we fail the check.
            case ArrayType arrayType -> false;
            case ObjectType objectType -> false;
            case FunctionType functionType -> false;

            // Primitive types don't have any valid supertypes except themselves and any
            case PrimitiveType primitiveType -> false;

            // Type arguments are considered equal if in this map
            case TypeArgument typeArgument when superType instanceof TypeArgument superTypeArgument -> equalTypeArgs.isEqual(typeArgument, superTypeArgument);
            case TypeArgument typeArgument -> isSubtype(typeArgument.bound(), superType, equalTypeArgs);

            // TODO: Consider shortcut for union supertype
            case UnionType(var children) -> children.stream().allMatch(child -> isSubtype(child, superType, equalTypeArgs));
        };
    }

    private static boolean isFunctionSubtype(TypeArgEqualityStack equalTypeArgs, Type functionType, Type superFunctionType) {
        // Abuse instanceof destructuring
        if (!(functionType instanceof FunctionType(
                var typeArguments,
                var args,
                var requiredArgs,
                var varargs,
                var returnType
        ))) return false;
        if (!(superFunctionType instanceof FunctionType(
                var superTypeArguments,
                var superArgs,
                var superRequiredArgs,
                var superVarargs,
                var superReturnType
        ))) return false;


        // Check if the type arguments are compatible
        if (typeArguments.size() != superTypeArguments.size()) {
            return false;
        }
        for (var i = 0; i < typeArguments.size(); i++) {
            var bound = typeArguments.get(i).bound();
            var superBound = superTypeArguments.get(i).bound();

            // Type argument bounds are apparently contravariant
            if (!isSubtype(superBound, bound, equalTypeArgs)) {
                return false;
            }
        }
        // Mark type arguments as equal
        equalTypeArgs.push(typeArguments, superTypeArguments);

        // If super can accept unlimited args, and we can't, this doesn't work
        if (superVarargs && !varargs) return false;
        // We can't require more args than the supertype, as we might not get them
        if (requiredArgs > superRequiredArgs) return false;
        if (!varargs && superArgs.size() > args.size()) return false;

        // First check arguments up to where one argument list ends
        var sharedArgs = Math.min(args.size(), superArgs.size());
        for (var i = 0; i < sharedArgs; i++) {
            var arg = args.get(i);
            var superArg = superArgs.get(i);

            // Arguments are contravariant
            if (!isSubtype(superArg, arg, equalTypeArgs)) {
                return false;
            }
        }
        // Match our varargs with super args
        if (args.size() < superArgs.size()) {
            for (var i = args.size(); i < superArgs.size(); i++) {
                var superArg = superArgs.get(i);
                if (!isSubtype(superArg, args.getLast(), equalTypeArgs)) {
                    return false;
                }
            }
        }
        // Match our end args with super varargs
        if (superArgs.size() < args.size() && superVarargs) {
            for (int i = superArgs.size(); i < args.size(); i++) {
                var arg = args.get(i);
                if (!isSubtype(superArgs.getLast(), arg, equalTypeArgs)) {
                    return false;
                }
            }
        }

        // Return types are covariant
        if (!isSubtype(returnType, superReturnType, equalTypeArgs)) {
            return false;
        }

        equalTypeArgs.pop();
        return true;
    }
}

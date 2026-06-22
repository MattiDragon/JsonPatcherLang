package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;

import java.util.*;

public class GenericTypeMatcher {
    private final Map<TypeArgument, List<Type>> typeArguments;

    public GenericTypeMatcher(Collection<TypeArgument> activeArguments) {
        this.typeArguments = new HashMap<>();
        for (var activeArgument : activeArguments) {
            typeArguments.put(activeArgument, new ArrayList<>());
        }
    }

    /**
     * Matches a type from the function signature with a type for an actual parameter.
     * In the process the values of any type arguments used are updated.
     *
     * @param expected The expected type from the function signature
     * @param actual The actual type passed into the parameter
     * @return {@code true} if the actual type matches the expected type, {@code false} otherwise.
     */
    public boolean match(Type expected, Type actual) {
        return switch (expected) {
            case TypeArgument typeArgument when typeArguments.containsKey(typeArgument) -> {
                if (TypeComparison.isSubtype(actual, typeArgument.bound())) {
                    // We don't want unknown in the union. In cases where an argument is unknown,
                    // we skip it and just rely on unknown always being a subtype of the union anyway.
                    if (actual != SpecialType.UNKNOWN) {
                        typeArguments.get(typeArgument).add(actual);
                    }
                    yield true;
                } else {
                    yield false;
                }
            }
            case ArrayType(var expectedComponent) -> {
                var actualComponent = TypeComparison.getArrayComponent(actual);
                if (actualComponent == null) yield false;
                yield match(expectedComponent, actualComponent);
            }
            case ObjectType(var expectedComponent) -> {
                var actualComponent = TypeComparison.getObjectComponent(actual);
                if (actualComponent == null) yield false;
                yield match(expectedComponent, actualComponent);
            }
            case FunctionType(var expectedTypeArgs, var expectedArgs, var expectedRequiredArgs,
                              var expectedVarargs, var expectedReturnType)
                    when actual instanceof FunctionType(var actualTypeArgs, var actualArgs, var actualRequiredArgs,
                                                        var actualVarargs, var actualReturnType) -> {

                // Do some matching for easy cases here
                for (var i = 0; i < Math.min(expectedRequiredArgs, actualRequiredArgs); i++) {
                    match(expectedArgs.get(i), actualArgs.get(i));
                }
                if (actualVarargs) {
                    for (var i = expectedRequiredArgs; i < expectedArgs.size(); i++) {
                        if (i < actualArgs.size()) {
                            match(expectedArgs.get(i), actualArgs.getLast());
                        } else {
                            match(expectedArgs.getLast(), actualArgs.getLast());
                        }
                    }
                }

                match(expectedReturnType, actualReturnType);

                // Do actual type checking here
                yield TypeComparison.isSubtype(actual, expected);
            }
            case UnionType(var children) -> {
                var matches = false;
                for (var child : children) {
                    if (match(child, actual)) {
                        matches = true;
                    }
                }
                yield matches;
            }
            case LazyType lazyType -> match(lazyType.get(), actual);
            default -> TypeComparison.isSubtype(actual, expected);
        };
    }

    public Type getType(TypeArgument argument) {
        if (!typeArguments.containsKey(argument)) {
            throw new NoSuchElementException(argument + " is not active in this matcher");
        }
        var types = typeArguments.get(argument);
        if (types.isEmpty()) {
            return argument.bound();
        }
        return UnionType.union(types);
    }

    public Type fillTemplate(Type type) {
        return switch (type) {
            case TypeArgument typeArgument -> {
                if (typeArguments.containsKey(typeArgument)) {
                    yield getType(typeArgument);
                } else {
                    yield typeArgument;
                }
            }
            case ArrayType(var component) -> new ArrayType(fillTemplate(component));
            case ObjectType(var component) -> new ObjectType(fillTemplate(component));
            case FunctionType(var typeArgs, var args, var requiredArgs, var varargs, var returnType) -> {
                var filledArgs = new ArrayList<Type>(args.size());
                for (var arg : args) {
                    filledArgs.add(fillTemplate(arg));
                }
                yield new FunctionType(typeArgs, filledArgs, requiredArgs, varargs, fillTemplate(returnType));
            }
            case UnionType(var children) -> UnionType.union(children.stream().map(this::fillTemplate).toList());
            case LazyType lazyType -> fillTemplate(lazyType.get());
            default -> type;
        };
    }
}

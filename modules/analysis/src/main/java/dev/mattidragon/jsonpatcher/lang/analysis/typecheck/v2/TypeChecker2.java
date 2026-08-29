package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.v2;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.*;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeChecker2 {
    private final TreeMetadata metadata;
    private final DiagnosticsBuilder diagnostics;
    private final PrimitiveProperties primitiveProperties;
    private final Deque<ReturnTypeConsumer> returnTypeConsumers = new ArrayDeque<>();

    private TypeChecker2(TreeMetadata metadata, DiagnosticsBuilder diagnostics, PrimitiveProperties primitiveProperties) {
        this.metadata = metadata;
        this.diagnostics = diagnostics;
        this.primitiveProperties = primitiveProperties;
    }

    public static void typeCheck(ProgramNode node, TreeMetadata metadata, PrimitiveProperties primitiveProperties, DiagnosticsBuilder diagnostics) {
        new TypeChecker2(metadata, diagnostics, primitiveProperties).typeCheck(node, true);
    }

    private void typeCheck(ProgramNode node, boolean apply) {
        switch (node) {
            case Expression e -> checkExpression(e, SpecialType.ANY, apply);

            case ReturnStatement(Optional<Expression> value) -> {
                if (returnTypeConsumers.isEmpty()) {
                    value.ifPresent(e -> typeCheck(e, apply));
                    break;
                }
                var consumer = returnTypeConsumers.peek();
                value.ifPresentOrElse(
                        e -> consumer.consumer.accept(checkExpression(e, consumer.expected, apply)),
                        () -> consumer.consumer.accept(PrimitiveType.NULL)
                );
            }

            case ImportStatement statement -> {
                // Copy type from statement to variable, setting to unknown if missing
                // The language server will set the types of import statements before type checking based on doc comments
                var type = metadata.get(statement, TypeChecker.TYPE).orElse(SpecialType.UNKNOWN);
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TypeChecker.TYPE, type));
            }
            case VariableCreationStatement statement -> {
                var existing = metadata.get(statement, TypeChecker.TYPE);
                var type = checkExpression(statement.initializer(), existing.orElse(SpecialType.ANY), apply);
                if (existing.isEmpty()) {
                    var variableType = statement.mutable() ? UnionType.union(type, SpecialType.UNKNOWN) : type;
                    metadata.put(statement, TypeChecker.TYPE, variableType);
                }

                var finalType = metadata.get(statement, TypeChecker.TYPE).orElseThrow();
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TypeChecker.TYPE, finalType));
            }
            case ForEachLoopStatement statement -> {
                var existingType = metadata.get(statement, TypeChecker.TYPE);
                var iterableType = checkExpression(statement.iterable(), new ArrayType(existingType.orElse(SpecialType.UNKNOWN)), apply);
                var variableType = TypeComparison.getArrayComponent(iterableType);
                if (variableType == null) variableType = SpecialType.UNKNOWN;

                var finalVariableType = variableType;
                metadata.put(statement, TypeChecker.TYPE, finalVariableType);
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TypeChecker.TYPE, finalVariableType));
            }
            case FunctionDeclarationStatement statement -> {
                var type = checkExpression(statement.value(), SpecialType.ANY, apply);
                metadata.put(statement, TypeChecker.TYPE, type);
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TypeChecker.TYPE, type));
            }

            case FunctionArgument argument -> {
                var existing = metadata.get(argument, TypeChecker.TYPE);
                var inferredType = argument.defaultValue().map(e -> checkExpression(e, existing.orElse(SpecialType.ANY), apply));

                if (existing.isEmpty() && inferredType.isPresent()) {
                    metadata.put(argument, TypeChecker.TYPE, inferredType.get());
                }

                metadata.get(argument, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TypeChecker.TYPE, existing.or(() -> inferredType).orElse(SpecialType.UNKNOWN)));
            }

            default -> node.getChildren().forEach(child -> typeCheck(child, apply));
        }
    }

    private void checkExpressionIfApply(Expression e, Type expected, boolean apply) {
        if (apply) {
            checkExpression(e, expected, true);
        }
    }

    /**
     * Performs type inference and checking
     * @param e The expression whose type should be inferred
     * @param expected The expected type, used as a hint in some cases and validated when {@code apply}
     * @param apply Whether to actually apply the inferred type to the metadata and report errors.
     *              If false, will still perform inference and return a type,
     *              but won't report any errors or apply the type to the metadata.
     * @return The inferred type of the expression, may not be subtype of {@code expected} if there are errors.
     */
    private Type checkExpression(Expression e, Type expected, boolean apply) {
        var type = switch (e) {
            case NumberExpression numberExpression -> PrimitiveType.NUMBER;
            case StringExpression stringExpression -> PrimitiveType.STRING;
            case BooleanExpression booleanExpression -> PrimitiveType.BOOLEAN;
            case NullExpression nullExpression -> PrimitiveType.NULL;
            // TODO: make root variables hold types and read here
            case RootExpression rootExpression -> PrimitiveType.OBJECT;

            case UnaryModificationExpression(var postfix, var target, var op) -> checkUnary(target, op, apply);
            case UnaryExpression(var input, var op) -> checkUnary(input, op, apply);

            case BinaryExpression(var first, var second, var op) -> checkBinary(e, expected, first, second, op, apply);
            case AssignmentExpression(var target, var value, var operator) -> checkBinary(e, expected, target, value, operator, apply);

            case TernaryExpression(var condition, var ifTrue, var ifFalse) -> {
                checkExpressionIfApply(condition, SpecialType.ANY, apply);
                var trueType = checkExpression(ifTrue, expected, apply);
                var falseType = checkExpression(ifFalse, expected, apply);
                yield UnionType.union(trueType, falseType);
            }

            case ShortedBinaryExpression(var first, var second, var op) -> {
                var firstType = checkExpression(first, SpecialType.ANY, apply);
                var secondType = checkExpression(second, SpecialType.ANY, apply);
                yield UnionType.union(firstType, secondType);
            }

            case ArrayInitializerExpression(var contents) -> {
                var expectedComponent = getArrayComponentTypeOrUnknown(expected);
                var types = contents.stream().map(child -> checkExpression(child, expectedComponent, apply)).toList();
                if (types.isEmpty()) {
                    yield expected;
                } else {
                    yield new ArrayType(UnionType.union(types));
                }
            }
            case ObjectInitializerExpression(var entries) -> {
                var resolvedTypes = ObjectTypes.resolveObjectTypes(expected);
                if (resolvedTypes.isEmpty()) {
                    yield inferMapType(entries);
                }

                var best = findBestObjectType(entries, resolvedTypes);
                if (apply) {
                    validatePropertyTypes(e, entries, best);
                }
                yield best.realType();
            }

            case IsInstanceExpression(var input, var valueType) -> {
                checkExpressionIfApply(input, SpecialType.ANY, apply);
                yield PrimitiveType.BOOLEAN;
            }

            case StringInterpolationExpression(List<String> parts, List<Expression> children) -> {
                for (var child : children) {
                    checkExpressionIfApply(child, SpecialType.ANY, apply);
                }
                yield PrimitiveType.STRING;
            }

            case VariableAccessExpression variableAccess -> metadata.get(variableAccess, VariableAnalyser.VARIABLE_REFERENCE)
                   .flatMap(variable -> metadata.get(variable, TypeChecker.TYPE))
                   .orElse(SpecialType.UNKNOWN);

            case IndexExpression(Expression parent, Expression index) -> {
                var union = UnionType.union(PrimitiveType.ARRAY, PrimitiveType.OBJECT, PrimitiveType.SPECIAL);
                var parentType = checkExpression(parent, union, apply);

                var isArray = isAssignable(PrimitiveType.ARRAY, parentType);
                var isObject = isAssignable(PrimitiveType.OBJECT, parentType);
                var isSpecial = isAssignable(PrimitiveType.SPECIAL, parentType);

                if (isArray && !isObject && !isSpecial) {
                    var componentType = getArrayComponentTypeOrUnknown(parentType);
                    checkExpressionIfApply(index, PrimitiveType.NUMBER, apply);
                    yield componentType;
                } else if (!isArray && isObject && !isSpecial) {
                    var componentType = getObjectComponentTypeOrUnknown(parentType);
                    checkExpressionIfApply(index, PrimitiveType.STRING, apply);
                    yield componentType;
                } else if (isArray && isObject && !isSpecial) {
                    var componentType = UnionType.union(getArrayComponentTypeOrUnknown(parentType), getObjectComponentTypeOrUnknown(parentType));
                    checkExpressionIfApply(index, UnionType.union(PrimitiveType.NUMBER, PrimitiveType.STRING), apply);
                    yield componentType;
                } else {
                    // Either there's a special type in the union, or we have an un-indexable type
                    // In both cases we don't know anything about the index type or the component type
                    checkExpressionIfApply(index, SpecialType.ANY, apply);
                    yield SpecialType.UNKNOWN;
                }
            }

            case PropertyAccessExpression(Expression parent, String name) -> {
                var parentType = checkExpression(parent, SpecialType.ANY, apply);
                var propertyType = TypeChecker.getPropertyType(name, parentType, primitiveProperties);
                if (propertyType == null) {
                    if (apply) {
                        diagnostics.addDiagnostic(new TypeCheckError(e,
                                metadata.get(e, MetadataKey.NAME_POS).orElse(null),
                                "Property " + name + " does not exist on type " + parentType,
                                TypeCheckError.Code.UNEXPECTED_PROPERTY));
                    }
                    yield SpecialType.UNKNOWN;
                } else {
                    yield propertyType;
                }
            }

            case FunctionExpression(Statement body, FunctionArguments args) -> {
                var inferredType = inferFunctionType(expected);

                inferredType.ifPresentOrElse(functionType -> {
                    for (var i = 0; i < args.arguments().size(); i++) {
                        var arg = args.arguments().get(i);
                        var lastArg = i == args.arguments().size() - 1;
                        var vararg = args.varargs() && lastArg;

                        Type argType;
                        if (i < functionType.args().size()) {
                            argType = functionType.args().get(i);
                        } else if (functionType.varargs()) {
                            argType = functionType.args().getLast();
                        } else {
                            if (apply) {
                                diagnostics.addDiagnostic(new TypeCheckError(arg,
                                        metadata.get(arg, MetadataKey.NAME_POS).orElse(null),
                                        "Too many arguments declared for function, expected " + functionType.args().size(),
                                        TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                            }
                            argType = SpecialType.UNKNOWN;
                        }

                        if (vararg && functionType.args().size() > args.arguments().size()) {
                            argType = UnionType.union(functionType.args().stream()
                                    .skip(args.arguments().size() - 1)
                                    .toList());
                        }

                        if (apply) {
                            var runtimeType = vararg ? new ArrayType(argType) : argType;
                            metadata.put(arg, TypeChecker.TYPE, runtimeType);
                        }
                    }

                    if (apply) {
                        if (functionType.varargs() && !args.varargs()) {
                            diagnostics.addDiagnostic(new TypeCheckError(args,
                                    metadata.get(args, MetadataKey.FULL_POS).orElse(null),
                                    "Expected varargs argument",
                                    TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                        }

                        if (args.requiredArguments() > functionType.requiredArgs()) {
                            diagnostics.addDiagnostic(new TypeCheckError(args,
                                    metadata.get(args, MetadataKey.FULL_POS).orElse(null),
                                    "Too many required arguments, expected at most " + functionType.requiredArgs(),
                                    TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                        }

                        if (!args.varargs() && functionType.args().size() > args.arguments().size()) {
                            diagnostics.addDiagnostic(new TypeCheckError(args,
                                    metadata.get(args, MetadataKey.FULL_POS).orElse(null),
                                    "Too few arguments declared for function, expected " + functionType.args().size(),
                                    TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                        }
                    }
                }, () -> {
                    for (var i = 0; i < args.arguments().size(); i++) {
                        var arg = args.arguments().get(i);
                        var vararg = args.varargs() && i == args.arguments().size() - 1;
                        if (apply) {
                            metadata.put(arg, TypeChecker.TYPE, vararg ? new ArrayType(SpecialType.UNKNOWN) : SpecialType.UNKNOWN);
                        }
                    }
                });

                var returnTypes = new ArrayList<Type>();
                returnTypeConsumers.push(new ReturnTypeConsumer(returnTypes::add, inferredType.map(FunctionType::returnType).orElse(SpecialType.UNKNOWN)));
                typeCheck(body, apply);
                returnTypeConsumers.pop();
                var returnType = UnionType.union(returnTypes);

                // TODO: maybe also infer from variable comments on args
                yield inferredType.orElseGet(() -> new FunctionType(
                        List.of(),
                        Stream.<Type>generate(() -> SpecialType.UNKNOWN).limit(args.arguments().size()).toList(),
                        args.requiredArguments(),
                        args.varargs(),
                        returnType
                ));
            }

            case FunctionCallExpression(Expression function, List<Expression> arguments) -> {
                var callableType = UnionType.union(PrimitiveType.FUNCTION, PrimitiveType.SPECIAL);

                var inferredFunctionType = inferFunctionType(checkExpression(function, callableType, apply));
                if (inferredFunctionType.isEmpty()) {
                    for (var argument : arguments) {
                        checkExpressionIfApply(argument, SpecialType.ANY, apply);
                    }
                    yield SpecialType.UNKNOWN;
                }

                record ArgTypeProbe(Type expected, Type actual) {
                }

                var functionType = inferredFunctionType.get();
                var genericMatcher = new GenericTypeMatcher(functionType.typeArguments());
                var argTypeProbes = new ArrayList<ArgTypeProbe>();

                for (var i = 0; i < arguments.size(); i++) {
                    Type expectedArgType;
                    if (i < functionType.args().size()) {
                        expectedArgType = functionType.args().get(i);
                    } else if (functionType.varargs()) {
                        expectedArgType = functionType.args().getLast();
                    } else {
                        expectedArgType = SpecialType.UNKNOWN;
                        if (apply) {
                            diagnostics.addDiagnostic(new TypeCheckError(arguments.get(i),
                                    metadata.get(arguments.get(i), MetadataKey.FULL_POS).orElse(null),
                                    "Too many arguments passed to function, expected at most " + functionType.args().size(),
                                    TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                        }
                    }
                    // Use generic matcher to fill with bounds
                    var probedArgType = checkExpression(arguments.get(i), genericMatcher.fillTemplate(expectedArgType), false);
                    argTypeProbes.add(new ArgTypeProbe(expectedArgType, probedArgType));
                }

                for (var argTypeProbe : argTypeProbes) {
                    genericMatcher.match(argTypeProbe.expected, argTypeProbe.actual);
                }

                for (var i = 0; i < arguments.size(); i++) {
                    var expectedArgType = genericMatcher.fillTemplate(i < functionType.args().size()
                            ? functionType.args().get(i)
                            : functionType.varargs() ? functionType.args().getLast() : SpecialType.UNKNOWN);
                    checkExpressionIfApply(arguments.get(i), expectedArgType, apply);
                }

                if (apply && arguments.size() < functionType.requiredArgs()) {
                    diagnostics.addDiagnostic(new TypeCheckError(e,
                            metadata.get(e, MetadataKey.FULL_POS).orElse(null),
                            "Too few arguments passed to function, expected at least " + functionType.requiredArgs(),
                            TypeCheckError.Code.ARGUMENT_COUNT_MISMATCH));
                }

                // TODO: also use expected return type in matching
                yield genericMatcher.fillTemplate(functionType.returnType());
            }

            case ErrorExpression expression -> {
                if (expression.child() != null) {
                    checkExpressionIfApply(expression.child(), expected, apply);
                }
                yield SpecialType.UNKNOWN;
            }

            default -> throw new IllegalStateException("Unexpected value: " + e);
        };

        if (apply) {
            metadata.put(e, TypeChecker.TYPE, type);
        }

        if (apply && !isAssignable(expected, type)) {
            diagnostics.addDiagnostic(new TypeCheckError(e,
                    metadata.get(e, MetadataKey.FULL_POS).orElse(null),
                    "Expected type " + expected + " but found " + type,
                    TypeCheckError.Code.UNEXPECTED_TYPE));
        }

        return type;
    }

    private Optional<FunctionType> inferFunctionType(Type expected) {
        return switch (expected) {
            case ArrayType arrayType -> Optional.empty();
            case FunctionType functionType -> Optional.of(functionType);
            case HardcodedType hardcodedType -> inferFunctionType(hardcodedType.base());
            case LazyType lazyType -> inferFunctionType(lazyType.get());
            case NamedType namedType -> namedType.callSignature();
            case ObjectType objectType -> Optional.empty();
            case PrimitiveType primitiveType -> Optional.empty();
            case SpecialType specialType -> Optional.empty();
            case TypeArgument typeArgument -> inferFunctionType(typeArgument.bound());
            case UnionType unionType -> unionType.children().stream()
                    .map(this::inferFunctionType)
                    .flatMap(Optional::stream)
                    .findFirst();
        };
    }

    private void validatePropertyTypes(Expression e, List<ObjectInitializerExpression.Entry> entries, ObjectTypes.ResolvedType best) {
        for (var entry : entries) {
            var property = best.property(entry.name());
            if (property.isEmpty()) {
                checkExpression(entry.value(), SpecialType.ANY, true);
                diagnostics.addDiagnostic(new TypeCheckError(entry,
                        metadata.get(entry, MetadataKey.NAME_POS).orElse(null),
                        "Property " + entry.name() + " does not exist on type " + best.realType(),
                        TypeCheckError.Code.UNEXPECTED_PROPERTY));
            } else {
                checkExpression(entry.value(), property.get().type(), true);
            }
        }
        var propNames = entries.stream().map(ObjectInitializerExpression.Entry::name).collect(Collectors.toSet());
        for (var prop : best.properties().entrySet()) {
            if (!prop.getValue().optional() && !propNames.contains(prop.getKey())) {
                diagnostics.addDiagnostic(new TypeCheckError(e,
                        metadata.get(e, MetadataKey.FULL_POS).map(p -> p.from().toSpan()).orElse(null),
                        "Missing required property " + prop.getKey() + " of type " + prop.getValue().type(),
                        TypeCheckError.Code.UNEXPECTED_PROPERTY));
            }
        }
    }

    private Type checkBinary(Expression e, Type expected, Expression first, Expression second, BinaryExpression.Operator op, boolean apply) {
        return switch (op) {
            case MINUS, DIVIDE, EXPONENT, MODULO -> {
                checkExpressionIfApply(first, PrimitiveType.NUMBER, apply);
                checkExpressionIfApply(second, PrimitiveType.NUMBER, apply);
                yield PrimitiveType.NUMBER;
            }

            case PLUS -> {
                var union = UnionType.union(PrimitiveType.NUMBER, PrimitiveType.STRING, SpecialType.ANY);
                var firstType = checkExpression(first, union, apply);
                var secondType = checkExpression(second, union, apply);

                if (isAssignable(PrimitiveType.NUMBER, firstType) && isAssignable(PrimitiveType.NUMBER, secondType)) {
                    yield PrimitiveType.NUMBER;
                    // TODO: Allow concat for non-string once supported
                } else if (isAssignable(PrimitiveType.STRING, firstType) && isAssignable(PrimitiveType.STRING, secondType)) {
                    yield PrimitiveType.STRING;
                } else {
                    if (apply) {
                        diagnostics.addDiagnostic(new TypeCheckError(e,
                                metadata.get(e, MetadataKey.FULL_POS).orElse(null),
                                "Expected both sides of + to be either number or string",
                                TypeCheckError.Code.UNEXPECTED_TYPE));
                    }
                    yield union;
                }
            }
            case MULTIPLY -> {
                var mulType = checkExpression(first, expected, apply);
                checkExpressionIfApply(second, PrimitiveType.NUMBER, apply);
                yield mulType;
            }

            case AND, OR, XOR -> {
                var union = UnionType.union(PrimitiveType.NUMBER, PrimitiveType.BOOLEAN);
                var firstType = checkExpression(first, union, apply);
                var secondType = checkExpression(second, union, apply);
                if (isAssignable(PrimitiveType.NUMBER, firstType) && isAssignable(PrimitiveType.NUMBER, secondType)) {
                    yield PrimitiveType.NUMBER;
                } else if (isAssignable(PrimitiveType.BOOLEAN, firstType) && isAssignable(PrimitiveType.BOOLEAN, secondType)) {
                    yield PrimitiveType.BOOLEAN;
                } else {
                    if (apply) {
                        diagnostics.addDiagnostic(new TypeCheckError(e,
                                metadata.get(e, MetadataKey.FULL_POS).orElse(null),
                                "Expected both sides of " + op + " to be either number or boolean",
                                TypeCheckError.Code.UNEXPECTED_TYPE));
                    }
                    yield union;
                }
            }

            case LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL -> {
                checkExpressionIfApply(first, PrimitiveType.NUMBER, apply);
                checkExpressionIfApply(second, PrimitiveType.NUMBER, apply);
                yield PrimitiveType.BOOLEAN;
            }

            case EQUALS, NOT_EQUALS -> {
                var firstType = checkExpression(first, SpecialType.ANY, apply);
                var secondType = checkExpression(second, SpecialType.ANY, apply);
                if (!isAssignable(firstType, secondType) && !isAssignable(secondType, firstType) && apply) {
                    diagnostics.addDiagnostic(new TypeCheckError(e,
                            metadata.get(e, MetadataKey.FULL_POS).orElse(null),
                            "Comparing values of type " + firstType + " and " + secondType + " will always be " + (op != BinaryExpression.Operator.EQUALS),
                            TypeCheckError.Code.TYPE_WARNING));
                }
                yield PrimitiveType.BOOLEAN;
            }

            case IN -> {
                var secondType = checkExpression(second, UnionType.union(PrimitiveType.ARRAY, PrimitiveType.OBJECT), apply);
                var isObject = isAssignable(PrimitiveType.OBJECT, secondType);
                var isArray = isAssignable(PrimitiveType.ARRAY, secondType);

                if (isObject && !isArray) {
                    checkExpressionIfApply(first, PrimitiveType.STRING, apply);
                } else if (!isObject && isArray) {
                    var componentType = getArrayComponentTypeOrUnknown(secondType);
                    checkExpressionIfApply(first, componentType, apply);
                } else if (isObject && isArray) {
                    var componentType = getArrayComponentTypeOrUnknown(secondType);
                    checkExpressionIfApply(first, UnionType.union(componentType, PrimitiveType.STRING), apply);
                } else {
                    // Shouldn't happen if second is valid
                    checkExpressionIfApply(first, SpecialType.ANY, apply);
                }
                yield PrimitiveType.BOOLEAN;
            }
            case ASSIGN -> checkExpression(second, checkExpression(first, expected, apply), apply);
        };
    }

    private PrimitiveType checkUnary(Expression input, UnaryExpression.Operator op, boolean apply) {
        return switch (op) {
            case NOT -> {
                checkExpressionIfApply(input, SpecialType.ANY, apply);
                yield PrimitiveType.BOOLEAN;
            }
            case MINUS, BITWISE_NOT, INCREMENT, DECREMENT -> {
                checkExpressionIfApply(input, PrimitiveType.NUMBER, apply);
                yield PrimitiveType.NUMBER;
            }
        };
    }

    private ObjectTypes.ResolvedType findBestObjectType(List<ObjectInitializerExpression.Entry> entries, List<ObjectTypes.ResolvedType> resolvedTypes) {
        if (resolvedTypes.isEmpty()) {
            throw new IllegalArgumentException("Can't find best object type if there are no resolved types");
        }

        var bestWeight = 0.0;
        ObjectTypes.ResolvedType best = null;

        for (var resolvedType : resolvedTypes) {
            var successes = 0;
            var failures = 0;
            for (var entry : entries) {
                var property = resolvedType.property(entry.name());
                if (property.isEmpty()) {
                    failures++;
                    continue;
                }
                var propertyType = property.get().type();
                if (isAssignable(propertyType, checkExpression(entry.value(), propertyType, false))) {
                    successes++;
                } else {
                    failures++;
                }
            }
            for (var prop : resolvedType.properties().entrySet()) {
                if (prop.getValue().optional()) {
                    continue;
                }
                var found = false;
                for (var entry : entries) {
                    if (entry.name().equals(prop.getKey())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    failures++;
                }
            }

            var weight = (double) successes / (successes + failures);
            if (weight > bestWeight || bestWeight == 0) {
                bestWeight = weight;
                best = resolvedType;
            }
        }
        return best;
    }

    private Type inferMapType(List<ObjectInitializerExpression.Entry> entries) {
        var types = entries.stream()
                .map(ObjectInitializerExpression.Entry::value)
                .map(child -> checkExpression(child, SpecialType.ANY, true))
                .toList();
        return new ArrayType(UnionType.union(types));
    }

    private static Type getArrayComponentTypeOrUnknown(Type secondType) {
        var componentType = TypeComparison.getArrayComponent(secondType);
        if (componentType == null) {
            componentType = SpecialType.UNKNOWN;
        }
        return componentType;
    }

    private static Type getObjectComponentTypeOrUnknown(Type secondType) {
        var componentType = TypeComparison.getObjectComponent(secondType);
        if (componentType == null) {
            componentType = SpecialType.UNKNOWN;
        }
        return componentType;
    }

    private boolean isAssignable(Type expected, Type type) {
        return TypeComparison.isSubtype(type, expected);
    }

    private record ReturnTypeConsumer(Consumer<Type> consumer, Type expected) {
    }
}

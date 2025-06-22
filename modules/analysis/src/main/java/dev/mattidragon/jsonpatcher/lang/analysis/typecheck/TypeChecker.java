package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TypeChecker {
    public static final MetadataKey<Type> TYPE = new MetadataKey<>("TypeChecker/TYPE");
    private static final Type CALLABLE_TYPE = new UnionType(Arrays.asList(PrimitiveType.FUNCTION, PrimitiveType.SPECIAL));

    private final TreeMetadata metadata;
    private final DiagnosticsBuilder diagnostics;
    private final PrimitiveProperties primitiveProperties;
    private final Deque<Consumer<Type>> returnTypeConsumers = new ArrayDeque<>();

    private TypeChecker(TreeMetadata metadata, DiagnosticsBuilder diagnostics, PrimitiveProperties primitiveProperties) {
        this.metadata = metadata;
        this.diagnostics = diagnostics;
        this.primitiveProperties = primitiveProperties;
    }

    public static void typeCheck(ProgramNode node, TreeMetadata metadata, PrimitiveProperties primitiveProperties, DiagnosticsBuilder diagnostics) {
        new TypeChecker(metadata, diagnostics, primitiveProperties).typeCheck(node);
    }

    private void typeCheck(ProgramNode node) {
        switch (node) {
            case Expression expression -> {
                expression.getChildren().forEach(this::typeCheck);
                metadata.put(expression, TYPE, checkExpression(expression));
            }
            case ImportStatement statement -> {
                // Copy type from statement to variable, setting to unknown if missing
                // The language server will set the types of import statements before type checking based on doc comments
                var type = metadata.get(statement, TYPE).orElse(SpecialType.UNKNOWN);
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TYPE, type));
            }
            case VariableCreationStatement statement -> {
                var type = checkExpression(statement.initializer());
                if (statement.mutable()) {
                    // We can't trust the initial type, so we do this to get rid of errors
                    type = UnionType.union(type, SpecialType.UNKNOWN);
                    metadata.put(statement, TYPE, type);
                }
                var finalType = type;
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TYPE, finalType));
            }
            case ForEachLoopStatement statement -> {
                var iterableType = checkExpression(statement.iterable());
                var variableType = getArrayComponent(iterableType);
                if (variableType == null) {
                    var message = "Expected any array, got " + TypeFormatter.format(iterableType);
                    var pos = metadata.get(statement.iterable(), MetadataKey.FULL_POS).orElse(null);
                    diagnostics.addDiagnostic(new TypeCheckError(statement, pos, message, TypeCheckError.Code.UNEXPECTED_TYPE));
                } else {
                    metadata.put(statement, TYPE, variableType);
                    metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                            .ifPresent(variable -> metadata.put(variable, TYPE, variableType));
                }
                typeCheck(statement.body());
            }
            case FunctionDeclarationStatement statement -> {
                var type = checkExpression(statement.value());
                metadata.put(statement, TYPE, type);
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> metadata.put(variable, TYPE, type));
            }

            case ReturnStatement(var value) -> {
                var type = value.map(this::checkExpression).orElse(PrimitiveType.NULL);
                var consumer = returnTypeConsumers.peek();
                if (consumer != null) {
                    consumer.accept(type);
                }
            }

            default -> node.getChildren().forEach(this::typeCheck);
        }
    }

    private Type checkExpression(Expression expression) {
        var existingType = metadata.get(expression, TYPE);
        if (existingType.isPresent()) {
            return existingType.get();
        }

        var result = switch (expression) {
            case NullExpression nullExpression -> PrimitiveType.NULL;
            case NumberExpression numberExpression -> PrimitiveType.NUMBER;
            case StringExpression stringExpression -> PrimitiveType.STRING;
            case BooleanExpression booleanExpression -> PrimitiveType.BOOLEAN;
            case RootExpression rootExpression -> PrimitiveType.OBJECT; // TODO: Some special casing needed here

            case VariableAccessExpression variableAccessExpression ->
                    metadata.get(variableAccessExpression, VariableAnalyser.VARIABLE_REFERENCE)
                            .flatMap(variable -> metadata.get(variable, TYPE))
                            .orElse(SpecialType.UNKNOWN);

            case FunctionExpression(var body, var args) -> checkFunctionDeclaration(body, args);
            case FunctionCallExpression(var function, var arguments) -> checkFunctionCall(function, arguments);

            case AssignmentExpression(var target, var value, var operator) -> {
                var oldType = checkExpression(target);
                var newType = checkBinaryOp(operator, checkExpression(target), checkExpression(value));
                if (oldType != PrimitiveType.NULL && !TypeComparison.isSubtype(newType, oldType)) {
                    addError(expression, "Expected " + TypeFormatter.format(oldType) + ", got " + TypeFormatter.format(newType));
                }
                yield newType;
            }
            case IndexExpression(var parent, var index) -> {
                var parentType = checkExpression(parent);

                yield switch (parentType) {
                    case ArrayType(var component) -> component;
                    case ObjectType(var component) -> component;
                    default -> SpecialType.UNKNOWN;
                };
            }
            case IsInstanceExpression(var input, var type) -> {
                checkExpression(input);
                yield PrimitiveType.BOOLEAN;
            }
            case PropertyAccessExpression(var parent, var name) ->
                checkPropertyAccess(parent, expression, name);

            case BinaryExpression(var first, var second, var op) ->
                    checkBinaryOp(op, checkExpression(first), checkExpression(second));
            case ShortedBinaryExpression(var first, var second, var op) ->
                    // Shorted and/or always returns either the first or the second value
                    UnionType.union(checkExpression(first), checkExpression(second));
            case UnaryExpression(var input, var op) ->
                    checkUnary(input, op);
            case UnaryModificationExpression(var postfix, var target, var op) ->
                    checkUnary(target, op);
            case TernaryExpression(var condition, var ifTrue, var ifFalse) -> {
                checkExpression(condition);
                yield UnionType.union(checkExpression(ifTrue), checkExpression(ifFalse));
            }

            case ArrayInitializerExpression(var contents) ->
                    new ArrayType(UnionType.union(contents.stream().map(this::checkExpression).toList()));
            case ObjectInitializerExpression(var contents) -> {
                if (contents.isEmpty()) {
                    yield PrimitiveType.OBJECT;
                }
                boolean isDict = true;
                Type componentType = null;
                for (var entry : contents) {
                    var valueType = checkExpression(entry.value());
                    if (componentType == null) {
                        componentType = valueType;
                    } else if (!TypeComparison.isSubtype(valueType, componentType)) {
                        isDict = false;
                    }
                }
                if (isDict) {
                    yield new ObjectType(componentType);
                } else {
                    yield PrimitiveType.OBJECT; // TODO: anonymous object types??
                }
            }

            default -> {
                expression.getChildren().forEach(this::typeCheck);
                yield SpecialType.UNKNOWN;
            }
        };

        while (result instanceof LazyType lazyType) {
            result = lazyType.get();
        }

        metadata.put(expression, TYPE, result);

        return result;
    }

    private Type checkPropertyAccess(Expression parent, Expression expression, String name) {
        var parentType = checkExpression(parent);
        var type = getPropertyType(name, parentType);
        if (type == null) {
            addError(expression, "Property '" + name + "' not found on type " + TypeFormatter.format(parentType));
            return SpecialType.UNKNOWN;
        }

        return type;
    }

    private @Nullable Type getPropertyType(String name, Type parentType) {
        return switch (parentType) {
            case ObjectType(var component) -> component;

            case ArrayType(var component) -> primitiveProperties.getPrimitivePropertyType(ValueType.ARRAY, name);
            case PrimitiveType.ARRAY -> primitiveProperties.getPrimitivePropertyType(ValueType.ARRAY, name);
            case PrimitiveType.NUMBER -> primitiveProperties.getPrimitivePropertyType(ValueType.NUMBER, name);
            case PrimitiveType.STRING -> primitiveProperties.getPrimitivePropertyType(ValueType.STRING, name);
            case PrimitiveType.BOOLEAN -> primitiveProperties.getPrimitivePropertyType(ValueType.BOOLEAN, name);
            case FunctionType functionType -> primitiveProperties.getPrimitivePropertyType(ValueType.FUNCTION, name);
            case PrimitiveType.FUNCTION -> primitiveProperties.getPrimitivePropertyType(ValueType.FUNCTION, name);

            case PrimitiveType.NULL, SpecialType.ANY -> null;

            case NamedType(var supertype, var properties, var wildcardPropertyType, var callSignature, var typeName) -> {
                var propType = properties.get(name);
                if (propType != null) {
                    yield propType;
                }
                if (wildcardPropertyType.isPresent()) {
                    yield wildcardPropertyType.get();
                } else {
                    yield null;
                }
            }

            case PrimitiveType.OBJECT, PrimitiveType.SPECIAL, SpecialType.UNKNOWN -> SpecialType.UNKNOWN;
            case SpecialType.NEVER -> SpecialType.NEVER;

            case TypeArgument typeArgument -> getPropertyType(name, typeArgument.bound());
            case UnionType unionType ->
                    UnionType.union(unionType.children()
                            .stream()
                            .map(type -> getPropertyType(name, type))
                            .filter(Objects::nonNull)
                            .toList());

            // Don't think this will actually ever happen, but it's easy to do
            case LazyType lazyType -> getPropertyType(name, lazyType.get());
        };
    }

    private Type checkFunctionDeclaration(Statement body, FunctionArguments args) {
        typeCheck(args);

        var returnTypes = new ArrayList<Type>();
        returnTypeConsumers.push(returnTypes::add);
        typeCheck(body);
        returnTypeConsumers.pop();
        var returnType = returnTypes.isEmpty() ? PrimitiveType.NULL : UnionType.union(returnTypes);

        return new FunctionType(
                List.of(),
                Stream.<Type>generate(() -> SpecialType.UNKNOWN).limit(args.arguments().size()).toList(),
                args.requiredArguments(),
                args.varargs(),
                returnType
        );
    }

    private Type checkFunctionCall(Expression function, List<Expression> arguments) {
        var actualArgTypes = arguments.stream().map(this::checkExpression).toList();
        var functionType = checkExpression(function);
        if (functionType instanceof NamedType namedType && namedType.callSignature().isPresent()) {
            functionType = namedType.callSignature().get();
        }

        if (!(functionType instanceof FunctionType(
                var typeArguments,
                var args,
                var requiredArgs,
                var varargs,
                var returnType
        ))) {
            if (!TypeComparison.isSubtype(functionType, CALLABLE_TYPE)) {
                addError(function, "Expected function or other callable, got " + TypeFormatter.format(functionType));
            }
            return SpecialType.UNKNOWN;
        }

        for (var i = 0; i < actualArgTypes.size(); i++) {
            var actualType = actualArgTypes.get(i);
            Type expectedType;
            if (i < args.size()) {
                expectedType = args.get(i);
            } else if (varargs) {
                expectedType = args.getLast();
            } else {
                addError(arguments.get(i), "Too many arguments");
                expectedType = SpecialType.UNKNOWN;
            }
            if (!TypeComparison.isSubtype(actualType, expectedType)) {
                addError(arguments.get(i), "Expected " + TypeFormatter.format(expectedType) + ", got " + TypeFormatter.format(actualType));
            }
        }
        if (actualArgTypes.size() < requiredArgs) {
            addError(function, "Expected at least " + requiredArgs + " arguments, got " + actualArgTypes.size());
        }

        if (returnType instanceof TypeArgument arg && typeArguments.contains(arg)) {
            // TODO: handle generic nicely
            return SpecialType.UNKNOWN;
        }

        return returnType;
    }

    private static Type checkBinaryOp(BinaryExpression.Operator op, Type firstType, Type secondType) {
        // TODO: Actual type checking
        // TODO: Prefer TypeComparison more
        return switch (op) {
            case EQUALS, NOT_EQUALS, LESS_THAN, LESS_THAN_EQUAL, GREATER_THAN, GREATER_THAN_EQUAL, IN ->
                    PrimitiveType.BOOLEAN;
            case PLUS -> {
                if (firstType == PrimitiveType.STRING && secondType == PrimitiveType.STRING) {
                    yield PrimitiveType.STRING;
                } else if (firstType == PrimitiveType.NUMBER && secondType == PrimitiveType.NUMBER) {
                    yield PrimitiveType.NUMBER;
                } else if (firstType instanceof ArrayType(var firstComponent) && secondType instanceof ArrayType(
                        var secondComponent
                )) {
                    yield new ArrayType(UnionType.union(firstComponent, secondComponent));
                } else if (firstType instanceof ObjectType(var firstComponent) && secondType instanceof ObjectType(
                        var secondComponent
                )) {
                    yield new ObjectType(UnionType.union(firstComponent, secondComponent));
                } else {
                    yield SpecialType.UNKNOWN;
                }
            }
            case MINUS, EXPONENT, MODULO, DIVIDE -> PrimitiveType.NUMBER;
            case MULTIPLY -> switch (firstType) {
                case PrimitiveType.ARRAY -> PrimitiveType.ARRAY;
                case ArrayType arrayType -> arrayType;
                case PrimitiveType.OBJECT -> PrimitiveType.OBJECT;
                case ObjectType objectType -> objectType;
                case PrimitiveType.STRING -> PrimitiveType.STRING;
                case PrimitiveType.NUMBER -> PrimitiveType.NUMBER;
                default -> SpecialType.UNKNOWN;
            };
            case AND, OR, XOR -> {
                if (firstType == PrimitiveType.BOOLEAN && secondType == PrimitiveType.BOOLEAN) {
                    yield PrimitiveType.BOOLEAN;
                } else if (firstType == PrimitiveType.NUMBER && secondType == PrimitiveType.NUMBER) {
                    yield PrimitiveType.NUMBER;
                } else {
                    yield SpecialType.UNKNOWN;
                }
            }
            case ASSIGN -> secondType;
        };
    }

    private Type checkUnary(Expression input, UnaryExpression.Operator op) {
        var type = checkExpression(input);
        switch (op) {
            case NOT -> {
                if (!TypeComparison.isSubtype(type, PrimitiveType.BOOLEAN)) {
                    addError(input, "Expected boolean, got " + TypeFormatter.format(type));
                }
            }
            case MINUS, BITWISE_NOT, INCREMENT, DECREMENT -> {
                if (!TypeComparison.isSubtype(type, PrimitiveType.NUMBER)) {
                    addError(input, "Expected number, got " + TypeFormatter.format(type));
                }
            }
        }
        return type;
    }

    private @Nullable Type getArrayComponent(Type arrayType) {
        return switch (arrayType) {
            case ArrayType(var component) -> component;
            case PrimitiveType.ARRAY, SpecialType.ANY, SpecialType.UNKNOWN -> SpecialType.UNKNOWN;
            case SpecialType.NEVER -> SpecialType.NEVER;
            case FunctionType functionType -> null;
            case NamedType namedType -> null;
            case ObjectType objectType -> null;
            case PrimitiveType primitiveType -> null;
            case TypeArgument typeArgument -> getArrayComponent(typeArgument.bound());
            case LazyType lazyType -> getArrayComponent(lazyType.get());
            case UnionType unionType -> UnionType.union(
                    unionType.children()
                            .stream()
                            .map(this::getArrayComponent)
                            .filter(Objects::nonNull)
                            .toList());
        };
    }

    private void addError(ProgramNode node, String message) {
        var pos = metadata.get(node, MetadataKey.MAIN_POS).orElse(null);
        diagnostics.addDiagnostic(new TypeCheckError(node, pos, message, TypeCheckError.Code.UNEXPECTED_TYPE));
    }
}

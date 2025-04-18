package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ReturnStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TypeChecker {
    public static final MetadataKey<Type> TYPE = new MetadataKey<>("TypeChecker/TYPE");

    private final Map<Variable, Type> variableTypes = new HashMap<>();
    private final TreeMetadata metadata;
    private final DiagnosticsBuilder diagnostics;
    private final Deque<Consumer<Type>> returnTypeConsumers = new ArrayDeque<>();

    private TypeChecker(TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.diagnostics = diagnostics;
    }

    public static void typeCheck(ProgramNode node, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        new TypeChecker(metadata, diagnostics).typeCheck(node);
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
                        .ifPresent(variable -> variableTypes.put(variable, type));
            }
            case VariableCreationStatement statement -> {
                var type = checkExpression(statement.initializer());
                metadata.get(statement, VariableAnalyser.VARIABLE_REFERENCE)
                        .ifPresent(variable -> variableTypes.put(variable, type));
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

        return switch (expression) {
            case NullExpression nullExpression -> PrimitiveType.NULL;
            case NumberExpression numberExpression -> PrimitiveType.NUMBER;
            case StringExpression stringExpression -> PrimitiveType.STRING;
            case BooleanExpression booleanExpression -> PrimitiveType.BOOLEAN;
            case RootExpression rootExpression -> PrimitiveType.OBJECT; // TODO: Some special casing needed here

            case VariableAccessExpression variableAccessExpression ->
                    metadata.get(variableAccessExpression, VariableAnalyser.VARIABLE_REFERENCE)
                            .flatMap(variable -> Optional.ofNullable(variableTypes.get(variable)))
                            .orElse(SpecialType.UNKNOWN);

            case FunctionExpression(var body, var args) -> checkFunctionDeclaration(body, args);
            case FunctionCallExpression(var function, var arguments) -> checkFunctionCall(function, arguments);

            case AssignmentExpression(var target, var value, var operator) -> {
                var oldType = checkExpression(target);
                var newType = checkBinaryOp(operator, checkExpression(target), checkExpression(value));
                if (oldType != PrimitiveType.NULL && !TypeComparison.isSubtype(newType, oldType)) {
                    addError(expression, "Expected " + oldType + ", got " + newType);
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
            case ObjectInitializerExpression(var contents) ->
                    new ObjectType(UnionType.union(
                            contents.stream()
                                    .map(ObjectInitializerExpression.Entry::value)
                                    .map(this::checkExpression)
                                    .toList()));

            default -> {
                expression.getChildren().forEach(this::typeCheck);
                yield SpecialType.UNKNOWN;
            }
        };
    }

    private Type checkPropertyAccess(Expression parent, Expression expression, String name) {
        var parentType = checkExpression(parent);
        return switch (parentType) {
            case ObjectType(var component) -> component;

            case ArrayType(var component) -> checkArrayProperty(expression, name);
            case PrimitiveType.ARRAY -> checkArrayProperty(expression, name);

            // TODO: primitives with stdlib

            case NamedType(var supertype, var properties, var callSignature) -> {
                var propType = properties.get(name);
                if (propType != null) {
                    yield propType;
                } else {
                    addError(expression, "Unknown property " + name + " on type " + supertype);
                    yield SpecialType.UNKNOWN;
                }
            }

            case PrimitiveType.OBJECT, SpecialType.UNKNOWN -> SpecialType.UNKNOWN;
            default -> {
                addError(expression, "");
                yield SpecialType.UNKNOWN;
            }
        };
    }

    private Type checkArrayProperty(Expression expression, String name) {
        // TODO: stdlib
        if (name.equals("length")) {
            return PrimitiveType.NUMBER;
        } else {
            addError(expression, "Array does not have property " + name);
            return SpecialType.UNKNOWN;
        }
    }

    private Type checkFunctionDeclaration(Statement body, FunctionArguments args) {
        typeCheck(args);

        var returnTypes = new ArrayList<Type>();
        returnTypeConsumers.push(returnTypes::add);
        typeCheck(body);
        returnTypeConsumers.pop();
        var returnType = UnionType.union(returnTypes);

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
        if (!(functionType instanceof FunctionType(
                var typeArguments,
                var args,
                var requiredArgs,
                var varargs,
                var returnType
        ))) {
            if (functionType != PrimitiveType.SPECIAL && functionType != PrimitiveType.FUNCTION) {
                addError(function, "Expected function or other callable, got " + functionType);
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
                addError(arguments.get(i), "Expected " + expectedType + ", got " + actualType);
            }
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
                    addError(input, "Expected boolean, got " + type);
                }
            }
            case MINUS, BITWISE_NOT, INCREMENT, DECREMENT -> {
                if (!TypeComparison.isSubtype(type, PrimitiveType.NUMBER)) {
                    addError(input, "Expected number, got " + type);
                }
            }
        }
        return type;
    }

    private void addError(ProgramNode node, String message) {
        var pos = metadata.get(node, MetadataKey.MAIN_POS).orElse(null);
        diagnostics.addDiagnostic(new TypeCheckError(node, pos, message, TypeCheckError.Code.UNEXPECTED_TYPE));
    }
}

package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.*;

import java.util.Locale;

public class TypeFormatter {
    private TypeFormatter() {
    }

    public static String format(Type type) {
        return switch (type) {
            case ArrayType(var component) -> {
                var out = new StringBuilder();
                var needsParens = component instanceof UnionType || component instanceof FunctionType;
                if (needsParens) out.append('{');
                out.append(format(component));
                if (needsParens) out.append('}');
                yield out.append("[]").toString();
            }
            case ObjectType(var component) -> {
                var out = new StringBuilder();
                var needsParens = component instanceof UnionType || component instanceof FunctionType;
                if (needsParens) out.append('{');
                out.append(format(component));
                if (needsParens) out.append('}');
                yield out.append("{}").toString();
            }
            case LazyType lazyType -> format(lazyType.get());
            case NamedType namedType -> namedType.name();
            case PrimitiveType primitiveType -> primitiveType.name().toLowerCase(Locale.ROOT);
            case SpecialType specialType -> specialType.name().toLowerCase(Locale.ROOT);
            case TypeArgument typeArgument -> "$" + typeArgument.name();
            case FunctionType functionType -> {
                var out = new StringBuilder();

                if (!functionType.typeArguments().isEmpty()) {
                    out.append('<');
                    var first = true;
                    for (var typeArgument : functionType.typeArguments()) {
                        if (first) first = false;
                        else out.append(", ");

                        out.append('$').append(typeArgument.name());
                        var bound = typeArgument.bound();
                        if (bound != SpecialType.ANY) {
                            out.append(": ").append(format(bound));
                        }
                    }
                    out.append('>');
                }

                out.append('(');
                var args = functionType.args();
                for (int i = 0; i < args.size(); i++) {
                    if (i != 0) {
                        out.append(", ");
                    }

                    var needsParens = i >= functionType.requiredArgs()
                                      && (args.get(i) instanceof UnionType || args.get(i) instanceof FunctionType);

                    if (needsParens) out.append('{');
                    out.append(format(args.get(i)));
                    if (needsParens) out.append('}');

                    if (i == args.size() - 1 && functionType.varargs()) {
                        out.append("*");
                    }
                    if (i >= functionType.requiredArgs()) {
                        out.append("?");
                    }
                }
                out.append(')');

                out.append(" -> ");

                var returnType = functionType.returnType();
                if (returnType instanceof UnionType) {
                    out.append('{');
                }
                out.append(format(returnType));
                if (returnType instanceof UnionType) {
                    out.append('}');
                }
                yield out.toString();
            }
            case UnionType unionType -> {
                var out = new StringBuilder();

                var first = true;
                for (var child : unionType.children()) {
                    if (first) first = false;
                    else out.append(" | ");

                    var needsParens = child instanceof FunctionType || child instanceof UnionType;

                    if (needsParens) out.append('{');
                    out.append(format(child));
                    if (needsParens) out.append('}');
                }

                yield out.toString();
            }
            case HardcodedType hardcodedType -> "{" + format(hardcodedType.base()) + "}+" + hardcodedType.kind().name().toLowerCase(Locale.ROOT);
        };
    }
}

package dev.mattidragon.jsonpatcher.docs.write;

import dev.mattidragon.jsonpatcher.docs.type.*;

public class DocTypeWriter {
    public static String write(DocType type) {
        return switch (type) {
            case ArrayDocType(var inner)
                    -> writeSuffix(inner, "[]");
            case MapDocType(var inner)
                    -> writeSuffix(inner, "{}");
            case ErrorDocType(var message)
                    -> "%error: " + message + "%";
            case FunctionDocType functionDocType
                    -> writeFunction(functionDocType);
            case ReferenceDocType(var name)
                    -> name;
            case TypeArgumentDocType(FunctionDocType.TypeArgument(var name, var bound))
                    -> "$" + name;
            case UnionDocType unionDocType
                    -> writeUnion(unionDocType);
        };
    }

    private static String writeSuffix(DocType inner, String suffix) {
        var needsParens = inner instanceof FunctionDocType || inner instanceof UnionDocType;

        var builder = new StringBuilder();
        if (needsParens) builder.append('{');
        builder.append(write(inner));
        if (needsParens) builder.append('}');
        builder.append(suffix);

        return builder.toString();
    }

    private static String writeFunction(FunctionDocType function) {
        var builder = new StringBuilder();

        if (!function.typeArguments().isEmpty()) {
            builder.append('<');
            var first = true;
            for (var typeArgument : function.typeArguments()) {
                if (first) first = false;
                else builder.append(", ");

                builder.append('$').append(typeArgument.name());
                typeArgument.bound().ifPresent(bound ->
                        builder.append(": ").append(write(bound)));
            }
            builder.append('>');
        }

        builder.append('(');
        var first = true;
        for (var argument : function.argTypes()) {
            if (first) first = false;
            else builder.append(", ");

            argument.name().ifPresent(name ->
                    builder.append(name).append(": "));

            var needsParens = argument.type() instanceof FunctionDocType || argument.type() instanceof UnionDocType;
            if (needsParens && argument.kind() != FunctionDocType.Argument.Kind.REGULAR) {
                builder.append('{');
            }
            builder.append(write(argument.type()));
            if (needsParens && argument.kind() != FunctionDocType.Argument.Kind.REGULAR) {
                builder.append('}');
            }
            switch (argument.kind()) {
                case VARARGS -> builder.append("*");
                case OPTIONAL -> builder.append("?");
            }
        }
        builder.append(") -> ").append(write(function.returnType()));
        return builder.toString();
    }

    private static String writeUnion(UnionDocType unionDocType) {
        var builder = new StringBuilder();

        var firstNeedsParens = unionDocType.first() instanceof FunctionDocType; // Only functions are ambiguous
        if (firstNeedsParens) builder.append('{');
        builder.append(write(unionDocType.first()));
        if (firstNeedsParens) builder.append('}');

        builder.append(" | ");

        var secondNeedsParens = unionDocType.second() instanceof FunctionDocType; // Only functions are ambiguous
        if (secondNeedsParens) builder.append('{');
        builder.append(write(unionDocType.second()));
        if (secondNeedsParens) builder.append('}');

        return builder.toString();
    }
}

package dev.mattidragon.jsonpatcher.docs.newdocs.write;

import dev.mattidragon.jsonpatcher.docs.newdocs.type.*;

public class DocTypeWriter {
    public static String write(NewDocType type) {
        return switch (type) {
            case ArrayDocType(var inner)
                    -> "[" + write(inner) + "]";
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

            builder.append(write(argument.type()));
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

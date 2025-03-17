package dev.mattidragon.jsonpatcher.docs.newdocs.write;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.DocCondition;

class DocConditionWriter {
    public static String write(DocCondition condition) {
        return switch (condition) {
            case DocCondition.AndCondition andCondition
                    -> writeAnd(andCondition);
            case DocCondition.LibraryGroupCondition(var name)
                    -> "#" + name;
            case DocCondition.MetadataCondition metadataCondition
                    -> writeMetadata(metadataCondition);
            case DocCondition.NotCondition notCondition
                    -> writeNot(notCondition);
            case DocCondition.OrCondition orCondition
                    -> writeOr(orCondition);
            case DocCondition.VersionCondition versionCondition
                    -> writeVersion(versionCondition);
        };
    }

    private static String writeAnd(DocCondition.AndCondition condition) {
        return write(condition.first()) + " & " + condition.second();
    }

    private static String writeOr(DocCondition.OrCondition condition) {
        var builder = new StringBuilder();

        var firstNeedsParens = condition.first() instanceof DocCondition.AndCondition;
        if (firstNeedsParens) builder.append("(");
        builder.append(write(condition.first()));
        if (firstNeedsParens) builder.append(")");

        builder.append(" | ");

        var secondNeedsParens = condition.second() instanceof DocCondition.AndCondition;
        if (secondNeedsParens) builder.append("(");
        builder.append(write(condition.second()));
        if (secondNeedsParens) builder.append(")");

        return builder.toString();
    }

    private static String writeNot(DocCondition.NotCondition condition) {
        var builder = new StringBuilder("!");

        var needsParens = condition.condition() instanceof DocCondition.AndCondition
                || condition.condition() instanceof DocCondition.OrCondition;

        if (needsParens) builder.append("(");
        builder.append(write(condition.condition()));
        if (needsParens) builder.append(")");

        return builder.toString();
    }

    private static String writeMetadata(DocCondition.MetadataCondition condition) {
        if (condition.value().isPresent()) {
            throw new UnsupportedOperationException("Value in metadata condition is not yet supported");
        }
        return "@" + condition.key();
    }

    private static String writeVersion(DocCondition.VersionCondition condition) {
        var modeSymbol = switch (condition.mode()) {
            case MAJOR -> '^';
            case MINOR -> '~';
            case EXACT -> '=';
            case GREATER -> '>';
            case LESSER -> '<';
        };
        return "v" + modeSymbol + condition.major() + "." + condition.minor() + "." + condition.patch();
    }
}

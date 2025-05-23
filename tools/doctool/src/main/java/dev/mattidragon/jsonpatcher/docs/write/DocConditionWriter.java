package dev.mattidragon.jsonpatcher.docs.write;

import dev.mattidragon.jsonpatcher.docs.data.DocCondition;

import java.util.stream.Collectors;

public class DocConditionWriter {
    public static String write(DocCondition condition) {
        return switch (condition) {
            case DocCondition.AndCondition andCondition
                    -> writeAnd(andCondition);
            case DocCondition.LibraryGroupCondition(var name)
                    -> "libgroup(" + name + ")";
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
        return condition.conditions()
                .stream()
                .map(DocConditionWriter::write)
                .collect(Collectors.joining(", ", "all(", ")"));
    }

    private static String writeOr(DocCondition.OrCondition condition) {
        return condition.conditions()
                .stream()
                .map(DocConditionWriter::write)
                .collect(Collectors.joining(", ", "any(", ")"));
    }

    private static String writeNot(DocCondition.NotCondition condition) {
        if (condition instanceof DocCondition.NotCondition(DocCondition.AndCondition(var children))) {
            return children.stream()
                    .map(DocConditionWriter::write)
                    .collect(Collectors.joining(", ", "none(", ")"));
        } else {
            return "not(" + write(condition.condition()) + ")";
        }
    }

    private static String writeMetadata(DocCondition.MetadataCondition condition) {
        if (condition.value().isPresent()) {
            throw new UnsupportedOperationException("Value in metadata condition is not yet supported");
        }
        return "metadata(" + condition.key() + ")";
    }

    private static String writeVersion(DocCondition.VersionCondition condition) {
        var modeSymbol = switch (condition.mode()) {
            case MAJOR -> '^';
            case MINOR -> '~';
            case EXACT -> '=';
            case GREATER -> '>';
            case LESSER -> '<';
        };
        return "version(" + modeSymbol + condition.major() + "." + condition.minor() + "." + condition.patch() + ")";
    }
}

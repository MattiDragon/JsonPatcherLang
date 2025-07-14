package dev.mattidragon.jsonpatcher.server.document.condition;

import dev.mattidragon.jsonpatcher.docs.data.DocCondition;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.*;
import dev.mattidragon.jsonpatcher.server.workspace.config.LangVersion;

import java.util.List;

public class DocConditionChecker {
    private final PatchMetadata patchMetadata;
    private final List<String> allowedLibraryGroups;
    private final LangVersion version;

    public DocConditionChecker(PatchMetadata patchMetadata, List<String> allowedLibraryGroups, LangVersion version) {
        this.patchMetadata = patchMetadata;
        this.allowedLibraryGroups = allowedLibraryGroups;
        this.version = version;
    }

    public boolean matches(DocCondition condition) {
        return switch (condition) {
            case DocCondition.AndCondition(var children) -> children.stream().allMatch(this::matches);
            case DocCondition.OrCondition(var children) -> children.stream().anyMatch(this::matches);
            case DocCondition.NotCondition(var child) -> !matches(child);
            case DocCondition.LibraryGroupCondition(var group) -> allowedLibraryGroups.contains(group);
            case DocCondition.MetadataCondition(var key, var value) -> patchMetadata.has(key)
                && value.map(element -> compareElement(element, patchMetadata.get(key))).orElse(true);
            case DocCondition.VersionCondition versionCondition -> versionCondition.matches(
                    version.major(), version.minor(), version.patch()
            );
        };
    }

    private boolean compareElement(MetadataElement actual, MetadataElement expected) {
        record Pair(MetadataElement left, MetadataElement right) {
        }

        return switch (new Pair(actual, expected)) {
            case Pair(MetadataString a, MetadataString e) -> a.value().equals(e.value());
            case Pair(MetadataNumber a, MetadataNumber e) -> a.value() == e.value();
            case Pair(MetadataBoolean a, MetadataBoolean e) -> a.value() == e.value();
            case Pair(MetadataNull a, MetadataNull e) -> true;
            case Pair(MetadataArray a, MetadataArray e) -> {
                if (a.values().size() != e.values().size()) {
                    yield false;
                }
                for (int i = 0; i < a.values().size(); i++) {
                    if (!compareElement(a.values().get(i), e.values().get(i))) {
                        yield false;
                    }
                }
                yield true;
            }
            case Pair(MetadataObject a, MetadataObject e) -> {
                if (a.values().size() != e.values().size()) {
                    yield false;
                }
                for (String key : a.values().keySet()) {
                    if (!e.values().containsKey(key)) {
                        yield false;
                    }
                    if (!compareElement(a.values().get(key), e.values().get(key))) {
                        yield false;
                    }
                }
                yield true;
            }
            default -> false;
        };
    }
}

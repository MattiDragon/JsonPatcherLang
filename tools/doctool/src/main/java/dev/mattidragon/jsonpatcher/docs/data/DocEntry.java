package dev.mattidragon.jsonpatcher.docs.data;

import dev.mattidragon.jsonpatcher.docs.tag.DocTag;
import dev.mattidragon.jsonpatcher.docs.type.DocType;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;
import java.util.Optional;

public sealed interface DocEntry extends MetadataHolder {
    SharedData sharedData();

    default NamespaceDescription namespace() {
        return sharedData().namespace();
    }

    default String name() {
        return sharedData().name();
    }

    default String body() {
        return sharedData().body();
    }

    @Override
    default Iterable<? extends DocEntry> getChildren() {
        return List.of();
    }

    record LibraryEntry(SharedData sharedData, Optional<String> location) implements DocEntry {
    }

    sealed interface GlobalEntry extends DocEntry {
    }

    record GlobalLibraryEntry(SharedData sharedData) implements GlobalEntry {
    }

    record GlobalValueEntry(SharedData sharedData, DocType type) implements GlobalEntry {
    }

    /**
     * The name may be {@code *} in addition to regular names
     */
    record PropertyEntry(SharedData sharedData, String owner, DocType type) implements DocEntry {
    }

    record TypeDeclarationEntry(SharedData sharedData, BaseType baseType) implements DocEntry {
        public enum BaseType {
            OBJECT,
            SPECIAL
        }
    }

    record TypeAliasEntry(SharedData sharedData, DocType definition) implements DocEntry {
    }

    record MetadataEntry(SharedData sharedData, DocType type) implements DocEntry {
    }

    record NamespaceEntry(SharedData sharedData) implements DocEntry {
    }

    record SharedData(NamespaceDescription namespace, String name, String body, List<DocTag> tags) {
    }
}
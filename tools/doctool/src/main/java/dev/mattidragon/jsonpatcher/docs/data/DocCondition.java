package dev.mattidragon.jsonpatcher.docs.data;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.MetadataElement;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.PatchMetadata;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public sealed interface DocCondition extends MetadataHolder {
    @Override
    Iterable<? extends MetadataHolder> getChildren();

    record LibraryGroupCondition(String group) implements DocCondition {
        @Override
        public Iterable<MetadataHolder> getChildren() {
            return List.of();
        }
    }

    record MetadataCondition(String key, Optional<MetadataElement> value) implements DocCondition {
        public boolean matches(PatchMetadata metadata) {
            if (!metadata.has(key)) return false;
            return value.map(required -> required.equals(metadata.get(key)))
                    .orElse(true);
        }

        @Override
        public Iterable<MetadataHolder> getChildren() {
            return value.stream().<MetadataHolder>map(Function.identity()).toList();
        }
    }

    record VersionCondition(int major, int minor, int patch, Mode mode) implements DocCondition {
        public boolean matches(int major, int minor, int patch) {
            return switch (mode) {
                case GREATER -> major > this.major
                                || (major == this.major && minor > this.minor)
                                || (major == this.major && minor == this.minor && patch > this.patch);
                case LESSER -> major < this.major
                                || (major == this.major && minor < this.minor)
                                || (major == this.major && minor == this.minor && patch < this.patch);
                case MAJOR -> major == this.major && (minor > this.minor || (minor == this.minor && patch >= this.patch));
                case MINOR -> major == this.major && minor == this.minor && patch >= this.patch;
                case EXACT -> major == this.major && minor == this.minor && patch == this.patch;
            };
        }

        @Override
        public Iterable<MetadataHolder> getChildren() {
            return List.of();
        }

        public enum Mode {
            MAJOR, MINOR, EXACT, GREATER, LESSER
        }
    }

    record OrCondition(List<DocCondition> conditions) implements DocCondition {
        public OrCondition {
            conditions = List.copyOf(conditions);
        }

        @Override
        public Iterable<DocCondition> getChildren() {
            return conditions;
        }
    }

    record AndCondition(List<DocCondition> conditions) implements DocCondition {
        public AndCondition {
            conditions = List.copyOf(conditions);
        }

        @Override
        public Iterable<DocCondition> getChildren() {
            return conditions;
        }
    }

    record NotCondition(DocCondition condition) implements DocCondition {
        @Override
        public Iterable<DocCondition> getChildren() {
            return List.of(condition);
        }
    }
}

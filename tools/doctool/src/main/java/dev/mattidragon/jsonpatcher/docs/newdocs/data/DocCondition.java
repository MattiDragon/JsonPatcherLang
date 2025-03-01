package dev.mattidragon.jsonpatcher.docs.newdocs.data;

import dev.mattidragon.jsonpatcher.lang.parse.metadata.MetadataElement;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.PatchMetadata;

import java.util.Optional;

public sealed interface DocCondition {
    record LibraryGroupCondition(String group) implements DocCondition {}

    record MetadataCondition(String key, Optional<MetadataElement> value) implements DocCondition {
        public boolean matches(PatchMetadata metadata) {
            if (!metadata.has(key)) return false;
            return value.map(required -> required.equals(metadata.get(key)))
                    .orElse(true);
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

        public enum Mode {
            MAJOR, MINOR, EXACT, GREATER, LESSER
        }
    }

    record OrCondition(DocCondition first, DocCondition second) implements DocCondition {}

    record AndCondition(DocCondition first, DocCondition second) implements DocCondition {}

    record NotCondition(DocCondition condition) implements DocCondition {}
}

package dev.mattidragon.jsonpatcher.docs.data;

import dev.mattidragon.jsonpatcher.docs.type.NewDocType;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;
import java.util.Optional;

public sealed interface NewDocEntry extends MetadataHolder {
    Optional<DocCondition> condition();
    NamespaceDescription namespace();
    String name();
    String body();

    @Override
    default Iterable<? extends NewDocEntry> getChildren() {
        return List.of();
    }

    record LibraryEntry(NamespaceDescription namespace, String name, Optional<String> location, Optional<DocCondition> condition, String body) implements NewDocEntry {
    }

    sealed interface GlobalEntry extends NewDocEntry {
    }

    record GlobalLibraryEntry(NamespaceDescription namespace, String name, Optional<DocCondition> condition, String body) implements GlobalEntry {
    }

    record GlobalValueEntry(NamespaceDescription namespace, String name, NewDocType type, Optional<DocCondition> condition, String body) implements GlobalEntry {
    }

    record PropertyEntry(NamespaceDescription namespace, String owner, String name, NewDocType type, Optional<DocCondition> condition, String body) implements NewDocEntry {
    }

    record TypeDeclarationEntry(NamespaceDescription namespace, String name, BaseType baseType, Optional<DocCondition> condition, String body) implements NewDocEntry {
        public enum BaseType {
            OBJECT,
            SPECIAL
        }
    }

    record TypeAliasEntry(NamespaceDescription namespace, String name, NewDocType definition, Optional<DocCondition> condition, String body) implements NewDocEntry {
    }

    record MetadataEntry(NamespaceDescription namespace, String name, NewDocType type, Optional<DocCondition> condition, String body) implements NewDocEntry {
    }

    record NamespaceEntry(NamespaceDescription namespace, String name, Optional<DocCondition> condition, String body) implements NewDocEntry {
    }
}

/*
library name
library name at "lib_location"

global library name
global name : type

property owner.name : type

type name
typealias name : definition

metadata name : type



number, string, boolean, null

type[]

(argType) -> returnType
(argName: argType) -> returnType
(argType1, argType2?) -> returnType
<T> (T) -> T
<T: type> (T[]) -> T


 */

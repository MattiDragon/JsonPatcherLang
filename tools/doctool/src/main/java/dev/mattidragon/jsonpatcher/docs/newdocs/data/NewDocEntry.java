package dev.mattidragon.jsonpatcher.docs.newdocs.data;

import dev.mattidragon.jsonpatcher.docs.newdocs.type.NewDocType;

import java.util.Optional;

public sealed interface NewDocEntry {
    Optional<DocCondition> condition();

    record LibraryEntry(String name, Optional<String> location, Optional<DocCondition> condition) implements NewDocEntry {
    }

    record GlobalLibraryEntry(String name, Optional<DocCondition> condition) implements NewDocEntry {
    }

    record GlobalValueEntry(String name, NewDocType type, Optional<DocCondition> condition) implements NewDocEntry {
    }

    record PropertyEntry() { }
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

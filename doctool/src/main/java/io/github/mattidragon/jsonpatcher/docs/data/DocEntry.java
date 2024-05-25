package io.github.mattidragon.jsonpatcher.docs.data;

public sealed interface DocEntry {
    String description();
    String name();
    
    record Module(String name, String description) implements DocEntry {}
    record Type(String name, DocType definition, String description) implements DocEntry {}
    record Value(String owner, String name, DocType definition, String description) implements DocEntry {}
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.environment;

public record LibraryGroup(String name) {
    public static final LibraryGroup DEFAULT = new LibraryGroup("default");
    public static final LibraryGroup INTERNALS = new LibraryGroup("jsonpatcher:internals");
}

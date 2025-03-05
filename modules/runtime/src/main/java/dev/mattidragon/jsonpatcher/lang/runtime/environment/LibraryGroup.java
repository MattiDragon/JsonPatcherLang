package dev.mattidragon.jsonpatcher.lang.runtime.environment;

public record LibraryGroup(String name) {
    public static final LibraryGroup DEFAULT = new LibraryGroup("default");
    public static final LibraryGroup INTERNALS = new LibraryGroup("jsonpatcher:internals");
    /**
     * Contains libraries that are dangerous to grant to untrusted code, as they allow arbitrary execution of java code.
     */
    public static final LibraryGroup REFLECTION = new LibraryGroup("jsonpatcher:reflection");
}

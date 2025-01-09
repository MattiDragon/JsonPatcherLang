package dev.mattidragon.jsonpatcher.lang.runtime;

@FunctionalInterface
public interface LibraryLocator {
    /**
     * Locates a library with the given name and puts it into the given object. All builtin libraries are already handled.
     * All errors thrown during library loading should be {@link PlatformContext#createException(String) created with} the passed {@code context} object 
     * to ensure that users get a proper stacktrace.
     *
     * @param libraryName   The name of the library to locate. This is the name given in the import statement. Implementation may choose any syntax they like for this.
     * @param libraryObject The object to load the library into. This object will be returned to the user.
     * @param context A context object providing various tools from the runtime.
     */
    void loadLibrary(String libraryName, Value.ObjectValue libraryObject, PlatformContext context);
}

package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

@FunctionalInterface
public interface LibraryLocator {
    /**
     * Locates a library with the given name and puts it into the given object. All builtin libraries are already handled.
     *
     * @param libraryName   The name of the library to locate. This is the name given in the import statement. Implementation may choose any syntax they like for this.
     * @param libraryObject The object to load the library into. This object will be returned to the user.
     * @param importPos     The position of the import statement. This can be used for error reporting.
     * @throws EvaluationException For any expected errors during loading, like a missing library or an error while calling it. This will give a nice stacktrace for the user.
     */
    void loadLibrary(String libraryName, Value.ObjectValue libraryObject, SourceSpan importPos, LangConfig config);
}

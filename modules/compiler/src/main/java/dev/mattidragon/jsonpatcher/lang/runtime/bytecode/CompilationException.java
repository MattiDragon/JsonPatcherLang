package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;

import java.util.Collection;
import java.util.stream.Collectors;

public class CompilationException extends RuntimeException {
    private final Collection<Diagnostic> errors;

    public CompilationException(Collection<Diagnostic> errors) {
        this.errors = errors;
    }

    @Override
    public String getMessage() {
        var errorMsg = errors.stream()
                .map(Diagnostic::toDisplay)
                .collect(Collectors.joining("\n"));
        return "Compilation failed due to errors:\n" + errorMsg;
    }
}

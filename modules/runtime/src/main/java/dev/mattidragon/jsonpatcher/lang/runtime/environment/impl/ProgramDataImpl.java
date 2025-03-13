package dev.mattidragon.jsonpatcher.lang.runtime.environment.impl;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.LibraryGroup;
import dev.mattidragon.jsonpatcher.lang.runtime.environment.ProgramData;

import java.util.List;

public record ProgramDataImpl(
        Program program,
        TreeMetadata metadata,
        String scriptName,
        String className,
        List<LibraryGroup> allowedLibraries
) implements ProgramData {
    public ProgramDataImpl {
        allowedLibraries = List.copyOf(allowedLibraries);
    }
}

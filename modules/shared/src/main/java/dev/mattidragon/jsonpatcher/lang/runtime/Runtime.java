package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An abstract runtime for the jsonpatcher language.
 */
public interface Runtime {
    Map<String, Runtime> RUNTIMES = ServiceLoader.load(Runtime.class).stream()
            .map(ServiceLoader.Provider::get)
            .collect(Collectors.toUnmodifiableMap(Runtime::getId, Function.identity()));
    
    String getId();
    
    /**
     * Prepares a program. A single prepared program can be executed multiple times.
     *
     * @param program The program to prepare
     * @param metadata Metadata about program nodes, like source code positions 
     * @return The prepared program
     */
    PreparedProgram prepare(Program program, TreeMetadata metadata);
}

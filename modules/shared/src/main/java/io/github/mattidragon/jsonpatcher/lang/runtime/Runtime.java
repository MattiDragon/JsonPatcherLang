package io.github.mattidragon.jsonpatcher.lang.runtime;

import io.github.mattidragon.jsonpatcher.lang.ast.Program;
import io.github.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An abstract runtime for the jsonpatcher language.
 * This class is <em>not</em> type-safe to allow easier usage of arbitrary implementations.
 * Implementations should check that any objects they receive are of the correct types.
 */
public interface Runtime {
    Map<String, Runtime> RUNTIMES = ServiceLoader.load(Runtime.class).stream()
            .map(ServiceLoader.Provider::get)
            .collect(Collectors.toUnmodifiableMap(Runtime::getId, Function.identity()));
    
    String getId();
    
    /**
     * Prepares a program. A single prepared program can be executed multiple times.
     *
     * @param program  The program to prepare
     * @param metadata
     * @return The prepared program
     */
    PreparedProgram prepare(Program program, TreeMetadata metadata);
}

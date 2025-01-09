package dev.mattidragon.jsonpatcher.lang.test;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.Map;

public interface TestRunner {
    String name();
    
    void executeCode(Program program, TreeMetadata metadata, Map<String, Value.ObjectValue> libraries);
}

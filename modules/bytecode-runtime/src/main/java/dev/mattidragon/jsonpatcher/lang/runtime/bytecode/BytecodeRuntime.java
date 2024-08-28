package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;

public class BytecodeRuntime implements Runtime {
    @Override
    public String getId() {
        return "jvm-bytecode";
    }

    @Override
    public PreparedProgram prepare(Program program, TreeMetadata metadata) {
        return null;
    }
}

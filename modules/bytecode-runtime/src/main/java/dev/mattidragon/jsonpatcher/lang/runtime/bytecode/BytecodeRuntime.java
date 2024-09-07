package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;

import java.util.function.Consumer;

public class BytecodeRuntime implements Runtime {
    @Override
    public String getId() {
        return "jvm-bytecode";
    }

    @Override
    public PreparedProgram prepare(Program program, TreeMetadata metadata, Consumer<PreparationContextBuilder> contextBuilder) {
        return null;
    }
}

package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;

public class LegacyRuntime implements Runtime {
    @Override
    public String getId() {
        return "legacy";
    }

    @Override
    public PreparedProgram prepare(Program program, TreeMetadata metadata) {
        return new LegacyRuntimePreparedProgram(program, metadata);
    }
}

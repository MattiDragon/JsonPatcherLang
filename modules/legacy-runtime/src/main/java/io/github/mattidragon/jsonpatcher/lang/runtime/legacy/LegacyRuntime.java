package io.github.mattidragon.jsonpatcher.lang.runtime.legacy;

import io.github.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import io.github.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import io.github.mattidragon.jsonpatcher.lang.ast.Program;
import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;

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

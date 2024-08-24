package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.ContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.ast.Program;

import java.util.function.Consumer;

public class LegacyRuntimePreparedProgram implements PreparedProgram {
    private final Program program;
    private final TreeMetadata metadata;

    public LegacyRuntimePreparedProgram(Program program, TreeMetadata metadata) {
        this.program = program;
        this.metadata = metadata;
    }

    @Override
    public void run(Consumer<ContextBuilder> contextBuilder, LangConfig config) {
        var builder = EvaluationContext.builder(config, metadata);
        contextBuilder.accept(builder);
        var context = builder.build();
        
        try {
            for (var statement : program.statements()) {
                StatementInterpreter.execute(statement, context);
            }
        } catch (ReturnException ignored) {
            // Catch returns to allow top level return to end script
        }
    }
}

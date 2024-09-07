package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparedProgram;
import dev.mattidragon.jsonpatcher.lang.runtime.RuntimeContextBuilder;

import java.util.function.Consumer;

public class LegacyRuntimePreparedProgram implements PreparedProgram {
    private final Program program;
    private final TreeMetadata metadata;
    private final VariableHolder variables;

    public LegacyRuntimePreparedProgram(Program program, TreeMetadata metadata, VariableHolder variables) {
        this.program = program;
        this.metadata = metadata;
        this.variables = variables;
    }

    @Override
    public void run(Consumer<RuntimeContextBuilder> contextBuilder, LangConfig config) {
        var builder = EvaluationContext.builder(config, metadata, variables);
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

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;
import dev.mattidragon.jsonpatcher.lang.error.PositionedException;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import org.jspecify.annotations.Nullable;

public class CompilationException extends PositionedException {
    private final @Nullable SourceSpan pos;
    
    public CompilationException(LangConfig config, String message, @Nullable SourceSpan pos) {
        super(config, message);
        this.pos = pos;
    }

    @Override
    protected String getBaseMessage() {
        return "Failed to compile";
    }

    @Override
    public @Nullable SourceSpan getPos() {
        return pos;
    }
}

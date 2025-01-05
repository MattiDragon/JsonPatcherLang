package dev.mattidragon.jsonpatcher.lang.runtime.legacy;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

/**
 * This exception is used for return statements.
 * Using a java exception makes this easier than handling returns at every point in the interpreter;
 */
public class ReturnException extends RuntimeException {
    public final Value value;
    public final @Nullable SourceSpan pos;

    public ReturnException(Value value, @Nullable SourceSpan pos) {
        super("A return exception wasn't handled. Something is wrong.");
        this.value = value;
        this.pos = pos;
    }
}

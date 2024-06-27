package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record IndexExpression(Expression parent, Expression index, SourceSpan pos) implements Reference {
    @Override
    public Value get(EvaluationContext context) {
        var parent = this.parent.evaluate(context);
        var index = this.index.evaluate(context);
        if (parent instanceof Value.ObjectValue objectValue) {
            if (!(index instanceof Value.StringValue stringValue))
                throw error(context, "Tried to index object by %s. Objects can only be indexed by string".formatted(index));
            return objectValue.get(stringValue.value(), context.config(), pos);
        }
        if (parent instanceof Value.ArrayValue arrayValue) {
            if (!(index instanceof Value.NumberValue numberValue))
                throw error(context, "Tried to index array by %s. Arrays can only be indexed by number.".formatted(index));
            return arrayValue.get((int) numberValue.value(), context.config(), pos);
        }
        throw error(context, "Tried to index %s with %s. Only arrays and objects are indexable.".formatted(parent, index));
    }

    @Override
    public void set(EvaluationContext context, Value value) {
        var parent = this.parent.evaluate(context);
        var index = this.index.evaluate(context);
        if (parent instanceof Value.ObjectValue objectValue) {
            if (!(index instanceof Value.StringValue stringValue))
                throw error(context, "Tried to index object by %s. Objects can only be indexed by string".formatted(index));
            objectValue.set(stringValue.value(), value, context.config(), pos);
            return;
        }
        if (parent instanceof Value.ArrayValue arrayValue) {
            if (!(index instanceof Value.NumberValue numberValue))
                throw error(context, "Tried to index array by %s. Arrays can only be indexed by number.".formatted(index));
            arrayValue.set((int) numberValue.value(), value, context.config(), pos);
            return;
        }
        throw error(context, "Tried to index %s with %s. Only arrays and objects are indexable.".formatted(parent, index));
    }

    @Override
    public void delete(EvaluationContext context) {
        var parent = this.parent.evaluate(context);
        var index = this.index.evaluate(context);
        if (parent instanceof Value.ObjectValue objectValue) {
            if (!(index instanceof Value.StringValue stringValue))
                throw error(context, "Tried to index object by %s. Objects can only be indexed by string".formatted(index));
            objectValue.remove(stringValue.value(), context.config(), pos);
            return;
        }
        if (parent instanceof Value.ArrayValue arrayValue) {
            if (!(index instanceof Value.NumberValue numberValue))
                throw error(context, "Tried to index array by %s. Arrays can only be indexed by number.".formatted(index));
            arrayValue.remove((int) numberValue.value(), context.config(), pos);
            return;
        }
        throw error(context, "Tried to index %s with %s. Only arrays and objects are indexable.".formatted(parent, index));
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(parent, index);
    }
}

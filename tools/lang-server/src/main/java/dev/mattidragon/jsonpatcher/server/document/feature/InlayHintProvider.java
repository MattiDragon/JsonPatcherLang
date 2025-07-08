package dev.mattidragon.jsonpatcher.server.document.feature;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.document.DocumentData;
import dev.mattidragon.jsonpatcher.server.document.DocumentState;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class InlayHintProvider {
    private final Supplier<CompletableFuture<DocumentData>> dataGetter;
    private final Supplier<Boolean> enabled;

    public InlayHintProvider(Supplier<CompletableFuture<DocumentData>> dataGetter, Supplier<Boolean> enabled) {
        this.dataGetter = dataGetter;
        this.enabled = enabled;
    }

    public CompletableFuture<List<InlayHint>> getHints(Range range) {
        return dataGetter.get().thenApplyAsync(
                data -> {
                    if (!enabled.get()) return List.of();

                    var file = data.sourceFile();
                    var span = new SourceSpan(
                            new SourcePos(file, range.getStart().getLine() + 1, range.getStart().getCharacter()),
                            new SourcePos(file, range.getEnd().getLine() + 1, range.getEnd().getCharacter())
                    );
                    var metadata = data.treeMetadata();
                    return data.lookups().variables()
                            .stream()
                            .map(variable -> makeVariableTypeHint(variable, span, metadata))
                            .filter(Objects::nonNull)
                            .toList();
                },
                Util.EXECUTOR);
    }

    private @Nullable InlayHint makeVariableTypeHint(Variable variable, SourceSpan span, TreeMetadata metadata) {
        // Other cases look bad
        if (!(variable.definition() instanceof VariableCreationStatement)) return null;
        var type = metadata.get(variable, TypeChecker.TYPE);
        return type.flatMap(value ->
                        metadata.get(variable.definition(), MetadataKey.NAME_POS)
                                .filter(pos -> span.contains(pos.from()) && span.contains(pos.to()))
                                .map(pos -> {
                                    var hint = new InlayHint();
                                    hint.setPosition(DocumentState.posToPosition(pos.to()));
                                    var label = new StringBuilder(": ");
                                    DefinitionFinder.writeType(value, label);
                                    hint.setLabel(label.toString());
                                    hint.setKind(InlayHintKind.Type);
                                    return hint;
                                }))
                .orElse(null);

    }
}

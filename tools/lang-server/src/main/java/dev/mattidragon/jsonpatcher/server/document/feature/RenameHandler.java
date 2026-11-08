package dev.mattidragon.jsonpatcher.server.document.feature;

import dev.mattidragon.jsonpatcher.formatter.printer.StringPrinter;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.document.DocumentData;
import dev.mattidragon.jsonpatcher.server.document.DocumentState;
import dev.mattidragon.jsonpatcher.server.index.IndexEntry;
import dev.mattidragon.jsonpatcher.server.index.StaticCombinedIndex;
import dev.mattidragon.jsonpatcher.server.index.symbol.*;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RenameHandler {
    private final Supplier<CompletableFuture<DocumentData>> dataGetter;
    private final WorkspaceManager workspace;

    public RenameHandler(Supplier<CompletableFuture<DocumentData>> dataGetter, WorkspaceManager workspace) {
        this.dataGetter = dataGetter;
        this.workspace = workspace;
    }

    public CompletableFuture<@Nullable Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepare(Position position) {
        return dataGetter.get().thenApplyAsync(data -> {
            var pos = new SourcePos(data.sourceFile(), position.getLine() + 1, position.getCharacter() + 1);
            var combinedIndex = new StaticCombinedIndex(data.index(), workspace.getWorkspaceIndex());

            var entries = combinedIndex.lookupEntries(pos).toList();
            if (entries.isEmpty()) {
                return null;
            }
            if (entries.size() != 1) {
                throw renameError("Multiple symbols found at the requested position");
            }

            var indexedToken = data.tokens().getAt(pos);
            if (indexedToken == null) {
                throw renameError("No token found at the requested position");
            }
            return Either3.forFirst(DocumentState.spanToRange(indexedToken.pos()));
        }, Util.EXECUTOR);
    }

    public CompletableFuture<@Nullable WorkspaceEdit> doRename(Position position, String newName) {
        return dataGetter.get().thenApplyAsync(data -> {
            var pos = new SourcePos(data.sourceFile(), position.getLine() + 1, position.getCharacter() + 1);
            var combinedIndex = new StaticCombinedIndex(data.index(), workspace.getWorkspaceIndex());

            var entries = combinedIndex.lookupEntries(pos).toList();
            if (entries.isEmpty()) {
                throw renameError("No symbol found at the requested position");
            }
            if (entries.size() != 1) {
                throw renameError("Multiple symbols found at the requested position");
            }

            var symbol = entries.getFirst().symbol();
            var changes = handleSymbol(symbol, newName, combinedIndex);
            return new WorkspaceEdit(changes);
        }, Util.EXECUTOR);
    }

    private Map<String, List<TextEdit>> handleSymbol(Symbol symbol, String newName, StaticCombinedIndex combinedIndex) {
        var stringFormatter = new StringFormatter();

        var edits = Stream.concat(
                combinedIndex.find(new IndexEntry(symbol, true))
                        .map(span -> renameSymbolAt(span, symbol, newName, true, stringFormatter)),
                combinedIndex.find(new IndexEntry(symbol, false))
                        .map(span -> renameSymbolAt(span, symbol, newName, false, stringFormatter))
        ).toList();
        return edits.stream().collect(Collectors.groupingBy(
                FileEdit::file,
                Collectors.mapping(FileEdit::edit, Collectors.toList())
        ));
    }

    private FileEdit renameSymbolAt(SourceSpan span, Symbol symbol, String newName, boolean declaration, StringFormatter stringFormatter) {
        return switch (symbol) {
            case VariableSymbol(var variable) -> {
                var formattedName = stringFormatter.format(newName);
                if (declaration && variable.definition() instanceof ImportStatement) {
                    // TODO: split the import statement if the new name is different from the imported name
                    throw renameError("Library symbol renaming is not yet supported");
                } else {
                    yield new FileEdit(span.from().file().name(), new TextEdit(DocumentState.spanToRange(span), formattedName));
                }
            }
            case TypeArgumentSymbol _ -> {
                var cleanedName = newName.startsWith("$") ? newName.substring(1) : newName;
                validateDocName(cleanedName, "Invalid type argument name");
                yield new FileEdit(span.from().file().name(), new TextEdit(DocumentState.spanToRange(
                        new SourceSpan(span.from().offset(1), span.to())
                ), cleanedName));
            }
            case MetadataSymbol _ -> {
                var formattedName = stringFormatter.format(newName);
                // If the metadata isn't documented, we can rename it despite cursed names,
                // but if it is documented, we need to match the doc identifier rules
                if (declaration) validateDocName(formattedName, "Invalid metadata name");
                yield new FileEdit(span.from().file().name(), new TextEdit(DocumentState.spanToRange(span), formattedName));
            }
            case GlobalSymbol _ -> {
                var formattedName = stringFormatter.format(newName);
                validateDocName(formattedName, "Invalid global name");
                yield new FileEdit(span.from().file().name(), new TextEdit(DocumentState.spanToRange(span), formattedName));
            }
            case PropertySymbol _ -> {
                var formattedName = stringFormatter.format(newName);
                validateDocName(formattedName, "Invalid property name");
                yield new FileEdit(span.from().file().name(), new TextEdit(DocumentState.spanToRange(span), formattedName));
            }
            case LibrarySymbol _ -> {
                // TODO: split the import statement if the new name is different from the imported name
                throw renameError("Library symbol renaming is not yet supported");
            }

            default -> throw renameError("Cannot rename symbol of type " + symbol.getClass().getSimpleName());
        };
    }

    private static void validateDocName(String formattedName, String invalidPropertyName) {
        if (!formattedName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw renameError(invalidPropertyName);
        }
    }

    private static ResponseErrorException renameError(String message) {
        return new ResponseErrorException(new ResponseError(ResponseErrorCode.RequestFailed, message, null));
    }

    private static class StringFormatter extends StringPrinter {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        protected void writeText(String text) {
            buffer.append(text);
        }

        String format(String text) {
            buffer.setLength(0);
            writeText(text);
            return buffer.toString();
        }
    }

    private record FileEdit(String file, TextEdit edit) {}
}

package dev.mattidragon.jsonpatcher.server.document.feature;

import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrintException;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrintOptions;
import dev.mattidragon.jsonpatcher.formatter.printer.PrettyPrinter;
import dev.mattidragon.jsonpatcher.formatter.printer.ProgramPrinter;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.document.DocumentData;
import dev.mattidragon.jsonpatcher.server.workspace.settings.SettingsManager;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class FormattingProvider {
    private final Supplier<CompletableFuture<DocumentData>> dataGetter;
    private final SettingsManager settingsManager;

    public FormattingProvider(Supplier<CompletableFuture<DocumentData>> dataGetter, SettingsManager settingsManager) {
        this.dataGetter = dataGetter;
        this.settingsManager = settingsManager;
    }

    public CompletableFuture<@Nullable List<? extends TextEdit>> format(FormattingOptions options) {
        return dataGetter.get().thenApplyAsync(
                data -> {
                    if (!settingsManager.settings().formatterEnabled()) return List.of();

                    var prettyPrinterOptions = convertOptions(options);
                    var prettyPrinter = new PrettyPrinter(prettyPrinterOptions, data.treeMetadata());

                    try {
                        ProgramPrinter.prettyPrint(data.program(), data.patchMetadata(), prettyPrinter);
                    } catch (PrettyPrintException e) {
                        var responseError = new ResponseError();
                        responseError.setCode(ResponseErrorCode.RequestFailed);
                        responseError.setMessage("Failed to format: " + e);
                        throw new ResponseErrorException(responseError);
                    }

                    var resultText = prettyPrinter.getOutput();
                    var lineCount = (int) data.sourceFile().code().chars().filter(i -> i == '\n').count();
                    var range = new Range(
                            new Position(0, 0),
                            new Position(lineCount + 1, 0)
                    );

                    return List.of(new TextEdit(range, resultText));
                },
                Util.EXECUTOR);
    }

    private PrettyPrintOptions convertOptions(FormattingOptions options) {
        var indent = options.isInsertSpaces() ? " ".repeat(options.getTabSize()) : "\t";
        return PrettyPrintOptions.builder()
                .indent(indent, options.getTabSize())
                .maxColumns(settingsManager.settings().formatterColumns())
                .build();
    }
}

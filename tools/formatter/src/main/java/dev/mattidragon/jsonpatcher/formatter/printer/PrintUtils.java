package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.parse.Token;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class PrintUtils {
    private PrintUtils() {}

    public static void printWithMultilineOption(PrintTarget target, Consumer<PrintTarget> inline, Consumer<PrintTarget> multiline) {
        var charCounter = target.newCharCounter();
        inline.accept(charCounter);
        if (charCounter.isMultiline()) {
            multiline.accept(target);
        } else {
            inline.accept(target);
        }
    }

    public static <T> void printCommaList(PrintTarget target,
                                          Iterable<T> entries,
                                          Consumer<PrintTarget> startPrinter,
                                          BiConsumer<T, PrintTarget> partPrinter,
                                          Consumer<PrintTarget> endPrinter) {
        printWithMultilineOption(target, inlineTarget -> {
            startPrinter.accept(inlineTarget);
            var first = true;
            for (T entry : entries) {
                if (first) first = false;
                else inlineTarget.write(Token.SimpleToken.COMMA).space();
                partPrinter.accept(entry, inlineTarget);
            }
            endPrinter.accept(inlineTarget);
        }, multilineTarget -> {
            startPrinter.accept(multilineTarget);
            multilineTarget.pushIndent().newLine();
            var first = true;
            for (T entry : entries) {
                if (first) first = false;
                else target.write(Token.SimpleToken.COMMA).newLine();
                partPrinter.accept(entry, multilineTarget);
            }
            multilineTarget.popIndent().newLine();
            endPrinter.accept(multilineTarget);
        });
    }
}

package dev.mattidragon.jsonpatcher.formatter.printer;

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
}

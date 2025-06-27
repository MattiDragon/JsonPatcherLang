package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.parse.Token;

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
                                          CommaListPartPrinter<T> partPrinter,
                                          Consumer<PrintTarget> endPrinter) {
        printWithMultilineOption(target, inlineTarget -> {
            startPrinter.accept(inlineTarget);
            var i = 0;
            for (T entry : entries) {
                if (i != 0) inlineTarget.write(Token.SimpleToken.COMMA).space();
                partPrinter.print(entry, inlineTarget, i);
                i++;
            }
            endPrinter.accept(inlineTarget);
        }, multilineTarget -> {
            startPrinter.accept(multilineTarget);
            multilineTarget.pushIndent().newLine();
            var i = 0;
            for (T entry : entries) {
                if (i != 0) target.write(Token.SimpleToken.COMMA).newLine();
                partPrinter.print(entry, multilineTarget, i);
                i++;
            }
            multilineTarget.popIndent().newLine();
            endPrinter.accept(multilineTarget);
        });
    }

    public static void printAttachedComment(ProgramNode node, PrintTarget target) {
        target.getMetadata(node, CommentAttacher.ATTACHED_COMMENT).ifPresent(block -> {
            for (var comment : block.comments()) {
                target.writeCommentLine(comment.text());
            }
        });
    }

    @FunctionalInterface
    public interface CommaListPartPrinter<T> {
        void print(T part, PrintTarget target, int index);
    }
}

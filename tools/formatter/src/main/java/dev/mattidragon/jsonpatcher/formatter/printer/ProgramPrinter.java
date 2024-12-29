package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.parse.PatchMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public class ProgramPrinter {
    public static void prettyPrint(Program program, PatchMetadata metadata, PrintTarget target) {
        metadata.getAll().forEach((key, value) -> {
            target.write(SimpleToken.AT_SIGN).write(new Token.WordToken(key));
            target.space();
            writeValueJson(value, target);
            target.write(SimpleToken.SEMICOLON).newLine();
        });

        if (!metadata.getAll().isEmpty()) {
            target.newLine();
        }

        StatementPrinter.writeStatementsWithSpacing(target, program.statements());
    }

    private static void writeValueJson(Value value, PrintTarget target) {
        if (target.isClosed()) return;
        switch (value) {
            case Value.ArrayValue(var children) -> PrintUtils.printCommaList(
                    target,
                    children,
                    target1 -> target1.write(SimpleToken.BEGIN_SQUARE),
                    ProgramPrinter::writeValueJson,
                    target1 -> target1.write(SimpleToken.END_SQUARE)
            );
            case Value.ObjectValue(var entries) -> PrintUtils.printCommaList(
                    target,
                    entries.entrySet(),
                    target1 -> target1.write(SimpleToken.BEGIN_SQUARE),
                    (entry, target1) -> {
                        target1.write(new Token.StringToken(entry.getKey()));
                        target1.write(SimpleToken.COLON).space();
                        writeValueJson(entry.getValue(), target1);
                    },
                    target1 -> target1.write(SimpleToken.END_SQUARE)
            );
            case Value.NumberValue(var number) -> target.write(new Token.NumberToken(number));
            case Value.StringValue(var string) -> target.write(new Token.StringToken(string));
            case Value.BooleanValue booleanValue -> target.write(booleanValue.value() ? KeywordToken.TRUE : KeywordToken.FALSE);
            case Value.NullValue nullValue -> target.write(KeywordToken.NULL);
            default -> throw new IllegalStateException("Illegal value in metadata: " + value);
        }
    }
}

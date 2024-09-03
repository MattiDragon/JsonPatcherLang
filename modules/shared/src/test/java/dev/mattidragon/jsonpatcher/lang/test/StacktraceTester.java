package dev.mattidragon.jsonpatcher.lang.test;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.runtime.legacy.EvaluationException;

// Quick tool to test stack trace logic that's difficult to automate.
public class StacktraceTester {
    private static final LangConfig CONFIG = new LangConfig(LangConfig.StackTraceMode.JAVA);

    public static void main(String[] args) {
        var file = new SourceFile("test file", "abcdefhijklmnop");
        var span = new SourceSpan(new SourcePos(file, 1, 2), new SourcePos(file, 1, 5));
        //noinspection CallToPrintStackTrace
        new EvaluationException(CONFIG,
                "error 1",
                span,
                new EvaluationException(CONFIG,
                        "error 2",
                        null,
                        new EvaluationException(CONFIG,
                                "error 3",
                                span,
                                new EvaluationException(CONFIG,
                                        "error 4",
                                        null))))
                .printStackTrace();
    }
}

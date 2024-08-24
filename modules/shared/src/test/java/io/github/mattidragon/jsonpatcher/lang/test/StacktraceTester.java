package io.github.mattidragon.jsonpatcher.lang.test;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.SimpleLangConfig;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceFile;
import io.github.mattidragon.jsonpatcher.lang.ast.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;

// Quick tool to test stack trace logic that's difficult to automate.
public class StacktraceTester {
    private static final LangConfig CONFIG = new SimpleLangConfig(true, true);

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

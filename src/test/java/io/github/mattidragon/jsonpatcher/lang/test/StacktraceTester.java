package io.github.mattidragon.jsonpatcher.lang.test;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceFile;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;

// Quick tool to test stack trace logic that's difficult to automate. Edit values in TestLangConfig to change mode.
public class StacktraceTester {
    public static void main(String[] args) {
        var file = new SourceFile("test file", "abcdefhijklmnop");
        var span = new SourceSpan(new SourcePos(file, 1, 2), new SourcePos(file, 1, 5));
        //noinspection CallToPrintStackTrace
        new EvaluationException("error 1", span, new EvaluationException("error 2", null, new EvaluationException("error 3", span, new EvaluationException("error 4", null)))).printStackTrace();
    }
}

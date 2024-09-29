package dev.mattidragon.jsonpatcher.lang.runtime.legacy.test;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.runtime.PreparationContextBuilder;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.legacy.LegacyRuntime;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;

import java.util.Map;

public class LegacyTestRunner implements TestRunner {
    private static final LangConfig LANG_CONFIG = new LangConfig(LangConfig.StackTraceMode.JAVA);
    
    @Override
    public String name() {
        return "legacy runtime";
    }

    @Override
    public void executeCode(Program program, TreeMetadata metadata, Map<String, Value.ObjectValue> libraries) {
        new LegacyRuntime().prepare(program, metadata, PreparationContextBuilder::declareStdlib)
                .run(builder -> builder.addStdlib().libraryLocator((name, object, context) -> object.value().putAll(libraries.get(name).value())), LANG_CONFIG);
    }
}

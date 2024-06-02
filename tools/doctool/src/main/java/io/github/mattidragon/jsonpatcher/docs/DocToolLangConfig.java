package io.github.mattidragon.jsonpatcher.docs;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;

public class DocToolLangConfig implements LangConfig {
    @Override
    public boolean useJavaStacktrace() {
        return false;
    }

    @Override
    public boolean useShortStacktrace() {
        return true;
    }
}

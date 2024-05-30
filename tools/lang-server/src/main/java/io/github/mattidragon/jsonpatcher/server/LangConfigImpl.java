package io.github.mattidragon.jsonpatcher.server;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;

public class LangConfigImpl implements LangConfig {
    @Override
    public boolean useJavaStacktrace() {
        return false;
    }

    @Override
    public boolean useShortStacktrace() {
        return true;
    }
}

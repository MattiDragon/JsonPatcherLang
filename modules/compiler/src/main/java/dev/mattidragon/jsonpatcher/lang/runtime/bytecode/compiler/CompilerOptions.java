package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;

public final class CompilerOptions {
    public static final CompilerOptions DEFAULT = builder().build();

    final boolean useDynamicConstants;
    final boolean useConstantFolding;
    final LangConfig langConfig;

    private CompilerOptions(boolean useDynamicConstants, boolean useConstantFolding, LangConfig langConfig) {
        this.useDynamicConstants = useDynamicConstants;
        this.useConstantFolding = useConstantFolding;
        this.langConfig = langConfig;
    }

    public LangConfig langConfig() {
        return langConfig;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean useDynamicConstants = true;
        private boolean useConstantFolding = true;
        private LangConfig langConfig = new LangConfig(LangConfig.StackTraceMode.JAVA);

        private Builder() {}

        public Builder disableDynamicConstants() {
            useDynamicConstants = false;
            return this;
        }

        public Builder disableConstantFolding() {
            useConstantFolding = false;
            return this;
        }

        public Builder langConfig(LangConfig langConfig) {
            this.langConfig = langConfig;
            return this;
        }

        public CompilerOptions build() {
            return new CompilerOptions(useDynamicConstants, useConstantFolding, langConfig);
        }
    }
}

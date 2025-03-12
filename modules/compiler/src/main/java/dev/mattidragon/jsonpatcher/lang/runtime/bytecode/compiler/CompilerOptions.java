package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler;

public final class CompilerOptions {
    public static final CompilerOptions DEFAULT = builder().build();

    final boolean useDynamicConstants;
    final boolean useConstantFolding;

    private CompilerOptions(boolean useDynamicConstants, boolean useConstantFolding) {
        this.useDynamicConstants = useDynamicConstants;
        this.useConstantFolding = useConstantFolding;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean useDynamicConstants = true;
        private boolean useConstantFolding = true;

        private Builder() {}

        public Builder disableDynamicConstants() {
            useDynamicConstants = false;
            return this;
        }

        public Builder disableConstantFolding() {
            useConstantFolding = false;
            return this;
        }

        public CompilerOptions build() {
            return new CompilerOptions(useDynamicConstants, useConstantFolding);
        }
    }
}

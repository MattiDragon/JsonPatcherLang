package dev.mattidragon.jsonpatcher.lang.error;

public record LangConfig(StackTraceMode stackTraceMode) {
    public enum StackTraceMode {
        JAVA,
        LONG,
        SHORT
    }
}

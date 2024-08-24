package dev.mattidragon.jsonpatcher.lang;

public record LangConfig(StackTraceMode stackTraceMode) {
    public enum StackTraceMode {
        JAVA,
        LONG,
        SHORT
    }
}

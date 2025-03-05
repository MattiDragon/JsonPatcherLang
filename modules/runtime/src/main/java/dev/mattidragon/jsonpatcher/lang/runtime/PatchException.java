package dev.mattidragon.jsonpatcher.lang.runtime;

public class PatchException extends RuntimeException {
    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, Exception cause) {
        super(message, cause);
    }
}

package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

public class PatchException extends RuntimeException {
    public PatchException(String message) {
        super(message);
    }

    public PatchException(String message, RuntimeException cause) {
        super(message, cause);
    }
}

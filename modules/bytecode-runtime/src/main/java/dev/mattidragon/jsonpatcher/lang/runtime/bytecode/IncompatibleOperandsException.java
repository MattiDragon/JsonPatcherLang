package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

public class IncompatibleOperandsException extends RuntimeException {
    public IncompatibleOperandsException(String message) {
        super(message);
    }
}

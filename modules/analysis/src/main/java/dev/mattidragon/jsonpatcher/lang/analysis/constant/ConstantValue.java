package dev.mattidragon.jsonpatcher.lang.analysis.constant;

public sealed interface ConstantValue {
    boolean asBoolean();

    record String(java.lang.String value) implements ConstantValue {
        @Override
        public boolean asBoolean() {
            return !value.isEmpty();
        }
    }

    record Number(double value) implements ConstantValue {
        @Override
        public boolean asBoolean() {
            return value != 0;
        }
    }

    enum Null implements ConstantValue {
        NULL;

        @Override
        public boolean asBoolean() {
            return false;
        }
    }

    enum Boolean implements ConstantValue {
        FALSE, TRUE;

        public static Boolean of(boolean value) {
            return value ? TRUE : FALSE;
        }

        public boolean value() {
            return this == TRUE;
        }

        @Override
        public boolean asBoolean() {
            return value();
        }
    }
}

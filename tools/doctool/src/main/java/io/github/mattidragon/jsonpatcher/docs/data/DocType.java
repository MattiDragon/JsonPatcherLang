package io.github.mattidragon.jsonpatcher.docs.data;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Represents types in documentation
 */
public sealed interface DocType {
    /**
     * Formats this type as it would appear in documentation. 
     * Parsing the output might yield different results for cases where the grammar is ambiguous.
     */
    String format();

    /**
     * Any specially named type.
     */
    enum Special implements DocType {
        /**
         * Any value of any runtime type
         */
        ANY,
        NUMBER,
        STRING,
        BOOLEAN,
        /**
         * An array, without particular care for the contents.
         */
        ARRAY,
        /**
         * An object, without particular care for the contents.
         */
        OBJECT,
        /**
         * A function, without particular care for arguments or return types
         */
        FUNCTION,
        NULL, 
        UNKNOWN;

        @Override
        public String format() {
            return name().toLowerCase(Locale.ROOT);
        }
        
        @Override
        public String toString() {
            return "Special[%s]".formatted(format());
        }
    }

    /**
     * A function with specific argument and return types. 
     */
    record Function(DocType returnType, List<Argument> args) implements DocType {
        public Function {
            args = List.copyOf(args);
        }

        @Override
        public String format() {
            return "(" + args.stream().map(Argument::format).collect(Collectors.joining(", ")) + ") -> " + returnType.format();
        }

        @Override
        public String toString() {
            return "Function[%s -> %s]".formatted(args, returnType);
        }

        public record Argument(String name, DocType type, boolean optional, boolean varargs) {
            public String format() {
                return name + (optional ? "?" : "") + (varargs ? "*" : "") + ": " + type.format();
            }

            @Override
            public String toString() {
                return "Argument[%s: %s%s]".formatted(name, type, (optional ? ", optional" : "") + (varargs ? ", varargs" : ""));
            }
        }
    }

    /**
     * An array with a specific element type.
     */
    record Array(DocType entry) implements DocType {
        @Override
        public String format() {
            return "[" + entry.format() + "]";
        }

        @Override
        public String toString() {
            return "Array[" + entry + "]";
        }
    }

    /**
     * An object with a specific value type. Keys are always strings.
     */
    record Object(DocType entry) implements DocType {
        @Override
        public String format() {
            return "{" + entry.format() + "}";
        }

        @Override
        public String toString() {
            return "Object[" + entry + "]";
        }
    }

    /**
     * A named type created using a {@code type} doc entry.
     */
    record Name(String name, SourceSpan pos) implements DocType {
        @Override
        public String format() {
            return name;
        }

        @Override
        public String toString() {
            return "Name[" + name + "]";
        }
    }

    /**
     * A union of multiple types.
     */
    record Union(List<DocType> children) implements DocType {
        public Union {
            children = List.copyOf(children);
        }

        @Override
        public String format() {
            return children.stream().map(DocType::format).collect(Collectors.joining(" | "));
        }

        @Override
        public String toString() {
            return "Union[" + children.stream().map(DocType::toString).collect(Collectors.joining(", ")) + "]";
        }
    }
}

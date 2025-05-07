package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record FunctionDocType(List<TypeArgument> typeArguments, List<Argument> argTypes, DocType returnType) implements DocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return Stream.concat(typeArguments.stream(), Stream.concat(argTypes.stream(), Stream.of(returnType))).toList();
    }

    public record TypeArgument(String name, Optional<DocType> bound) implements MetadataHolder {
        @Override
        public Iterable<? extends MetadataHolder> getChildren() {
            return bound.stream().toList();
        }
    }

    public record Argument(DocType type, Optional<String> name, Kind kind) implements MetadataHolder {
        @Override
        public Iterable<? extends MetadataHolder> getChildren() {
            return List.of(type);
        }

        public enum Kind {
            REGULAR, OPTIONAL, VARARGS
        }
    }
}

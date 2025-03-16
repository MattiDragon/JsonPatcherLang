package dev.mattidragon.jsonpatcher.docs.newdocs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record FunctionDocType(List<TypeArgument> typeArguments, List<Argument> argTypes, NewDocType returnType) implements NewDocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return Stream.concat(typeArguments.stream(), Stream.concat(argTypes.stream(), Stream.of(returnType))).toList();
    }

    public record TypeArgument(String name, Optional<NewDocType> bound) implements MetadataHolder {
        @Override
        public Iterable<? extends MetadataHolder> getChildren() {
            return bound.stream().toList();
        }
    }

    public record Argument(NewDocType type, Optional<String> name, Kind kind) implements MetadataHolder {
        @Override
        public Iterable<? extends MetadataHolder> getChildren() {
            return List.of(type);
        }

        public enum Kind {
            REGULAR, OPTIONAL, VARARGS
        }
    }
}

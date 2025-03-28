package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

public record TypeArgument(String name, Type bound) implements Type {
    @Override
    public String toString() {
        return name + ": " + bound;
    }
}

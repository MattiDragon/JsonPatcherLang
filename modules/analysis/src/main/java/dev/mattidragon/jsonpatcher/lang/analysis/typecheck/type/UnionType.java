package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

public record UnionType(List<Type> children) implements Type {
    public UnionType {
        children = simplify(children);
    }

    public static Type union(Type... types) {
        return union(Arrays.asList(types));
    }

    public static Type union(List<Type> types) {
        var children = simplify(types);
        if (children.isEmpty()) {
            return SpecialType.NEVER;
        } else if (children.size() == 1) {
            return children.getFirst();
        } else {
            return new UnionType(children);
        }
    }

    /**
     * Flattens any directly nested union types and remove easy duplicates.
     * @param children The types to flatten
     * @return An immutable list of flattened types
     */
    private static List<Type> simplify(List<Type> children) {
        // Use set to filter obvious duplicates. Linked to preserve order.
        var out = new LinkedHashSet<Type>();
        children.stream().flatMap(UnionType::flatten).forEach(out::addLast);
        return List.copyOf(out);
    }

    public static Stream<Type> flatten(Type type) {
        if (type instanceof UnionType(var children)) {
            return children.stream()
                    .filter(t -> t != SpecialType.NEVER)
                    .flatMap(UnionType::flatten);
        } else {
            return Stream.of(type);
        }
    }
}

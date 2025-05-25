package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.TypeArgument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

// TODO: apparently this class uses unchecked: fix or suppress
/**
 * A layered equality map for type arguments. Used to check if two type arguments are equal while checking compatibility
 * between two function types. Needs to be layered as the same type argument might appear multiple times from different
 * instances of the same function. Due to lexical scoping we can safely let one shadow another.
 */
public class TypeArgEqualityStack {
    private final List<Frame> stack = new ArrayList<>();

    public void push(List<TypeArgument> a, List<TypeArgument> b) {
        if (a.size() != b.size()) {
            throw new IllegalArgumentException("Type argument lists must be of the same size");
        }

        var equality = Map.ofEntries(
                IntStream.range(0, a.size())
                        .mapToObj(i -> List.of(Map.entry(a.get(i), b.get(i)), Map.entry(b.get(i), a.get(i))))
                        .flatMap(List::stream)
                        .<Map.Entry<TypeArgument, TypeArgument>>toArray(Map.Entry[]::new)
        );

        stack.add(new Frame(equality));
    }

    public void pop() {
        if (stack.isEmpty()) {
            throw new IllegalStateException("No frames to pop");
        }
        stack.removeLast();
    }

    public boolean isEqual(TypeArgument a, TypeArgument b) {
        if (a == b) return true;

        for (var frame : stack) {
            var equality = frame.equality.get(a);
            if (equality == b) {
                return true;
            }
        }

        return false;
    }

    private record Frame(Map<TypeArgument, TypeArgument> equality) {
    }
}

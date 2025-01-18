package dev.mattidragon.jsonpatcher.lang.error;

import java.util.*;

public final class Diagnostics {
    public static final Diagnostics EMPTY = new Diagnostics(List.of());

    private final List<Diagnostic> values;

    Diagnostics(List<Diagnostic> values) {
        this.values = values;
    }

    public Collection<Diagnostic> all() {
        return values;
    }

    public Collection<Diagnostic> get(Diagnostic.Kind... kinds) {
        var kindList = Arrays.asList(kinds);
        return values.stream()
                .filter(it -> kindList.contains(it.kind()))
                .toList();
    }

    public Collection<Diagnostic> errors() {
        return get(Diagnostic.Kind.ERROR, Diagnostic.Kind.INTERNAL_ERROR);
    }

    public Collection<Diagnostic> warnings() {
        return get(Diagnostic.Kind.WARNING, Diagnostic.Kind.UNUSED);
    }

    public Collection<Diagnostic> errorsAndWarnings() {
        return get(Diagnostic.Kind.ERROR, Diagnostic.Kind.INTERNAL_ERROR, Diagnostic.Kind.WARNING);
    }

    public Diagnostics join(Diagnostics other) {
        var list = new ArrayList<>(values);
        list.addAll(other.values);
        return new Diagnostics(Collections.unmodifiableList(list));
    }
}

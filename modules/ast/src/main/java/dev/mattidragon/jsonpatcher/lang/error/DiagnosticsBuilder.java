package dev.mattidragon.jsonpatcher.lang.error;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class DiagnosticsBuilder {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public DiagnosticsBuilder() {}

    public synchronized void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public synchronized Diagnostics build() {
        return new Diagnostics(List.copyOf(diagnostics));
    }

    public synchronized Diagnostics build(DiagnosticFilter filter) {
        return new Diagnostics(diagnostics.stream().filter(Predicate.not(filter::shouldBlock)).toList());
    }
}

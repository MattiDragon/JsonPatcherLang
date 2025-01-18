package dev.mattidragon.jsonpatcher.lang.error;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsBuilder {
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public DiagnosticsBuilder() {}

    public void addDiagnostic(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public Diagnostics build() {
        return new Diagnostics(List.copyOf(diagnostics));
    }
}

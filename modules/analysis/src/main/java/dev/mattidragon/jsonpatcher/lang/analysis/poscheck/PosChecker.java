package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;

/**
 * Validates that source positions are reasonable in a given AST
 */
public class PosChecker {
    private final TreeMetadata metadata;
    private final DiagnosticsBuilder diagnostics;

    private PosChecker(TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.diagnostics = diagnostics;
    }

    public static void analyse(ProgramNode rootNode, TreeMetadata metadata, DiagnosticsBuilder diagnostics) {
        // If the root node lacks pos metadata, ignore it
        var fullPos = metadata.get(rootNode, MetadataKey.FULL_POS);
        if (fullPos.isEmpty()) {
            diagnostics.addDiagnostic(new MissingMetadataError(rootNode, MetadataKey.FULL_POS));
            return;
        }

        var checker = new PosChecker(metadata, diagnostics);
        for (var child : rootNode.getChildren()) {
            checker.analyse(child, fullPos.get());
        }
    }

    private void analyse(ProgramNode node, SourceSpan parentSpan) {
        var fullPos = metadata.get(node, MetadataKey.FULL_POS);
        if (fullPos.isEmpty()) {
            diagnostics.addDiagnostic(new MissingMetadataError(node, MetadataKey.FULL_POS));
            return;
        }
        var mainPos = metadata.get(node, MetadataKey.MAIN_POS).orElseThrow();

        checkNesting(node, MetadataKey.FULL_POS, fullPos.get(), parentSpan);
        checkNesting(node, MetadataKey.MAIN_POS, mainPos, fullPos.get());

        for (var child : node.getChildren()) {
            analyse(child, fullPos.get());
        }
    }

    private void checkNesting(ProgramNode node, MetadataKey<SourceSpan> key, SourceSpan child, SourceSpan parent) {
        // Validate positions. If either is broken, we abort
        if (validateSpan(child) || validateSpan(parent)) return;

        var illegal = parent.from().file() != child.from().file();
        if (child.from().row() < parent.from().row()) illegal = true;
        if (child.from().row() == parent.from().row() && child.from().column() < parent.from().column()) illegal = true;
        if (child.to().row() > parent.to().row()) illegal = true;
        if (child.to().row() == parent.to().row() && child.to().column() > parent.to().column()) illegal = true;

        if (illegal) {
            diagnostics.addDiagnostic(new IllegalPosNestingError(node, key, child, parent));
        }
    }

    private boolean validateSpan(SourceSpan span) {
        var from = span.from();
        var to = span.to();

        var illegal = from.file() != to.file();
        if (from.row() > to.row()) illegal = true;
        if (from.row() == to.row() && from.column() > to.column()) illegal = true;

        if (illegal) {
            diagnostics.addDiagnostic(new IllegalSourceSpanError(span));
        }

        return illegal;
    }
}

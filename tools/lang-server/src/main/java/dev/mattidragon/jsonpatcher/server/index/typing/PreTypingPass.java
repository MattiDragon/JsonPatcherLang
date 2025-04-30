package dev.mattidragon.jsonpatcher.server.index.typing;

import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.SpecialType;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.server.index.IndexingDiagnostic;

public class PreTypingPass {
    private final TreeMetadata metadata;
    private final DocTypeConverter types;
    private final DiagnosticsBuilder diagnostics;

    public PreTypingPass(TreeMetadata metadata, DocTypeConverter types, DiagnosticsBuilder diagnostics) {
        this.metadata = metadata;
        this.types = types;
        this.diagnostics = diagnostics;
    }

    public static void apply(Program program, TreeMetadata metadata, DocTypeConverter types, DiagnosticsBuilder diagnostics) {
        new PreTypingPass(metadata, types, diagnostics).type(program);
    }

    private void type(ProgramNode node) {
        switch (node) {
            case ImportStatement statement ->
                    types.getLibraryType(statement.libraryName()).ifPresentOrElse(
                            type -> metadata.put(statement, TypeChecker.TYPE, type),
                            () -> {
                                diagnostics.addDiagnostic(new IndexingDiagnostic(
                                        statement,
                                        metadata.get(statement, MetadataKey.IMPORT_LOCATION_POS).orElse(null),
                                        "Unknown library: " + statement.libraryName(),
                                        IndexingDiagnostic.Code.UNKNOWN_LIBRARY)
                                );
                                metadata.put(statement, TypeChecker.TYPE, SpecialType.UNKNOWN);
                            });
            case VariableAccessExpression expression -> {
                var optionalVariable = metadata.get(expression, VariableAnalyser.VARIABLE_REFERENCE);
                if (optionalVariable.isEmpty()) break;
                var variable = optionalVariable.get();

                if (!(variable.definition() instanceof Program)) return;
                types.getGlobalType(variable.name()).ifPresentOrElse(
                        type -> metadata.put(expression, TypeChecker.TYPE, type),
                        () -> {
                            diagnostics.addDiagnostic(new IndexingDiagnostic(
                                    expression,
                                    metadata.get(expression, MetadataKey.MAIN_POS).orElse(null),
                                    "Unknown global: " + variable.name(),
                                    IndexingDiagnostic.Code.UNKNOWN_GLOBAL)
                            );
                            metadata.put(expression, TypeChecker.TYPE, SpecialType.UNKNOWN);
                        });
            }
            default -> node.getChildren().forEach(this::type);
        }
    }
}

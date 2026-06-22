package dev.mattidragon.jsonpatcher.server.index.typing;

import dev.mattidragon.jsonpatcher.docs.parse.DocParseDiagnostic;
import dev.mattidragon.jsonpatcher.docs.parse.Tokenizer;
import dev.mattidragon.jsonpatcher.docs.parse.TypeParser;
import dev.mattidragon.jsonpatcher.docs.type.DocType;
import dev.mattidragon.jsonpatcher.docs.type.ErrorDocType;
import dev.mattidragon.jsonpatcher.lang.analysis.comment.CommentAttacher;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.TypeChecker;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.SpecialType;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.Type;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ForEachLoopStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.CommentHandler;
import dev.mattidragon.jsonpatcher.server.index.IndexingDiagnostic;
import dev.mattidragon.jsonpatcher.toolcommon.typing.DocTypeConverter;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreTypingPass {
    private static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile(" *@@type +(.+)");
    private static final Pattern ARG_COMMENT_PATTERN = Pattern.compile(" *@@arg +(\\S+) +(.+)");

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
            case VariableCreationStatement statement -> {
                metadata.get(statement, CommentAttacher.ATTACHED_COMMENT)
                        .map(this::parseTypeFromComment)
                        .ifPresent(type -> metadata.put(statement, TypeChecker.TYPE, type));
                node.getChildren().forEach(this::type);
            }
            case FunctionDeclarationStatement statement -> {
                // TODO: consider supporting type comments as well
                metadata.get(statement, CommentAttacher.ATTACHED_COMMENT)
                        .map(this::parseFunctionArgsFromComment)
                        .ifPresent(args -> {
                            for (var argument : statement.value().args().arguments()) {
                                var type = args.get(argument.target());
                                if (type == null) continue;
                                metadata.put(argument, TypeChecker.TYPE, type);
                            }
                        });
                node.getChildren().forEach(this::type);
            }
            case ForEachLoopStatement statement -> {
                metadata.get(statement, CommentAttacher.ATTACHED_COMMENT)
                        .map(this::parseTypeFromComment)
                        .ifPresent(type -> metadata.put(statement, TypeChecker.TYPE, type));
                node.getChildren().forEach(this::type);
            }
            default -> node.getChildren().forEach(this::type);
        }
    }

    private @Nullable Type parseTypeFromComment(CommentAttacher.Block block) {
        Type type = null;
        for (var comment : block.comments()) {
            var matcher = TYPE_COMMENT_PATTERN.matcher(comment.text().stripTrailing());
            if (!matcher.matches()) continue;
            var typeText = matcher.group(1);
            var docType = parseDocType(comment, matcher, typeText);
            type = types.convert(docType);
        }
        return type;
    }

    private Map<FunctionArgument.Target, Type> parseFunctionArgsFromComment(CommentAttacher.Block block) {
        var args = new HashMap<FunctionArgument.Target, Type>();
        for (var comment : block.comments()) {
            var matcher = ARG_COMMENT_PATTERN.matcher(comment.text().stripTrailing());
            if (!matcher.matches()) continue;
            var nameText = matcher.group(1);
            var typeText = matcher.group(2);

            var docType = parseDocType(comment, matcher, typeText);
            var type = types.convert(docType);

            var target = nameText.equals("$") ? FunctionArgument.Target.Root.INSTANCE : new FunctionArgument.Target.Variable(nameText);
            args.put(target, type);
        }
        return args;
    }

    private DocType parseDocType(CommentHandler.Comment comment, Matcher matcher, String typeText) {
        var tokens = new Tokenizer(typeText, comment.start().offset(matcher.start(1)));

        try {
            return TypeParser.parse(tokens, metadata, diagnostics);
        } catch (Tokenizer.EolException e) {
            diagnostics.addDiagnostic(new DocParseDiagnostic(e.getPos().toSpan(), "Unexpected end of line", DocParseDiagnostic.Type.EOL));
            return new ErrorDocType("unexpected eol");
        }
    }
}

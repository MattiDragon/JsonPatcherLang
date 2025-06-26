package dev.mattidragon.jsonpatcher.server.document.feature;

import dev.mattidragon.jsonpatcher.docs.DocMetadataKeys;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.type.*;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ForEachLoopStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;
import dev.mattidragon.jsonpatcher.server.document.DocumentData;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class SemanticTokenizer {
    private final List<String> BUILT_IN_TYPES = Arrays.asList("number", "string", "boolean", "null",
            "object", "array", "function", "special", "any", "never", "unknown");
    public static final Map<String, Integer> TOKEN_TYPES;
    public static final Map<String, Integer> TOKEN_MODIFIERS;
    public static final SemanticTokensLegend LEGEND;
    
    static {
        var tokenTypes = new HashMap<String, Integer>();
        var supportedTokens = List.of(
                SemanticTokenTypes.Keyword,
                SemanticTokenTypes.Operator,

                SemanticTokenTypes.Namespace,
                SemanticTokenTypes.Type,
                SemanticTokenTypes.Function,
                SemanticTokenTypes.Parameter,
                SemanticTokenTypes.TypeParameter,
                SemanticTokenTypes.Variable,
                SemanticTokenTypes.Property,
                SemanticTokenTypes.Decorator,
                
                SemanticTokenTypes.String,
                SemanticTokenTypes.Number
        );
        for (var i = 0; i < supportedTokens.size(); i++) {
            tokenTypes.put(supportedTokens.get(i), i);
        }
        TOKEN_TYPES = Collections.unmodifiableMap(tokenTypes);
        var tokenModifiers = new HashMap<String, Integer>();
        
        var supportedModifiers = List.of(
                SemanticTokenModifiers.Declaration,
                SemanticTokenModifiers.Readonly,
                SemanticTokenModifiers.DefaultLibrary
        );
        for (var i = 0; i < supportedModifiers.size(); i++) {
            tokenModifiers.put(supportedModifiers.get(i), i);
        }
        TOKEN_MODIFIERS = Collections.unmodifiableMap(tokenModifiers);
        
        LEGEND = new SemanticTokensLegend(supportedTokens, supportedModifiers);
    }

    private final DataBuilder builder = new DataBuilder();
    private final TreeMetadata metadata;
    private final DocHolder docHolder;

    private SemanticTokenizer(DocumentData documentData, DocHolder docHolder) {
        metadata = documentData.treeMetadata();
        this.docHolder = docHolder;
    }

    public static SemanticTokens getTokens(DocumentData documentData, DocHolder docHolder) {
        var tokenizer = new SemanticTokenizer(documentData, docHolder);
        tokenizer.tokenizeDocs(documentData.docs());
        tokenizer.tokenize(documentData.program());
        return new SemanticTokens(tokenizer.builder.build());
    }

    private void tokenizeDocs(List<DocEntry> entries) {
        for (var entry : entries) {
            metadata.get(entry, DocMetadataKeys.NAMESPACE_POSITIONS).ifPresent(positions -> {
                for (var pos : positions) {
                    builder.addToken(pos, SemanticTokenTypes.Namespace);
                }
            });

            switch (entry) {
                case DocEntry.GlobalValueEntry globalValueEntry -> {
                    builder.addToken(metadata.get(globalValueEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Variable, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                    tokenizeDocType(globalValueEntry.type());
                }
                case DocEntry.GlobalLibraryEntry globalLibraryEntry ->
                        builder.addToken(metadata.get(globalLibraryEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Variable, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                case DocEntry.LibraryEntry libraryEntry -> {
                    builder.addToken(metadata.get(libraryEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                    builder.addToken(metadata.get(libraryEntry, MetadataKey.IMPORT_LOCATION_POS), SemanticTokenTypes.String);
                }
                case DocEntry.MetadataEntry metadataEntry -> {
                    builder.addToken(metadata.get(metadataEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Decorator, SemanticTokenModifiers.Declaration);
                    tokenizeDocType(metadataEntry.type());
                }
                case DocEntry.NamespaceEntry namespaceEntry ->
                        builder.addToken(metadata.get(namespaceEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Declaration);
                case DocEntry.PropertyEntry propertyEntry -> {
                    builder.addToken(metadata.get(propertyEntry, DocMetadataKeys.PROPERTY_OWNER_POS), SemanticTokenTypes.Type);
                    builder.addToken(metadata.get(propertyEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Property, SemanticTokenModifiers.Declaration);
                    tokenizeDocType(propertyEntry.type());
                }
                case DocEntry.TypeAliasEntry typeAliasEntry -> {
                    builder.addToken(metadata.get(typeAliasEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Type, SemanticTokenModifiers.Declaration);
                    tokenizeDocType(typeAliasEntry.definition());
                }
                case DocEntry.TypeDeclarationEntry typeDeclarationEntry -> {
                    builder.addToken(metadata.get(typeDeclarationEntry, MetadataKey.NAME_POS), SemanticTokenTypes.Type, SemanticTokenModifiers.Declaration);
                    builder.addToken(metadata.get(typeDeclarationEntry, DocMetadataKeys.BASE_TYPE_POS), SemanticTokenTypes.Type);
                }
            }
        }
    }
    
    private void tokenizeDocType(DocType type) {
        switch (type) {
            case ArrayDocType(var component) -> tokenizeDocType(component);
            case MapDocType(var component) -> tokenizeDocType(component);
            case UnionDocType(var first, var second) -> {
                tokenizeDocType(first);
                tokenizeDocType(second);
            }
            case ErrorDocType ignored -> {
            }
            case FunctionDocType(var typeArguments, var argTypes, var returnType) -> {
                for (var typeArgument : typeArguments) {
                    builder.addToken(metadata.get(typeArgument, MetadataKey.NAME_POS), SemanticTokenTypes.TypeParameter, SemanticTokenModifiers.Declaration);
                }
                for (var argType : argTypes) {
                    tokenizeDocType(argType.type());
                    if (argType.name().isPresent()) {
                        builder.addToken(metadata.get(argType, MetadataKey.NAME_POS), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Declaration);
                    }
                }
                tokenizeDocType(returnType);
            }
            case ReferenceDocType referenceDocType -> {
                if (BUILT_IN_TYPES.contains(referenceDocType.name())) {
                    builder.addToken(metadata.get(referenceDocType, MetadataKey.NAME_POS), SemanticTokenTypes.Type, SemanticTokenModifiers.DefaultLibrary);
                } else {
                    builder.addToken(metadata.get(referenceDocType, MetadataKey.NAME_POS), SemanticTokenTypes.Type);
                    metadata.get(referenceDocType, DocMetadataKeys.NAMESPACE_POSITIONS).orElse(List.of())
                            .forEach(pos -> builder.addToken(pos, SemanticTokenTypes.Namespace));
                }
            }
            case TypeArgumentDocType typeArgumentDocType ->
                    builder.addToken(metadata.get(typeArgumentDocType, MetadataKey.NAME_POS), SemanticTokenTypes.TypeParameter);
        }
    }

    private void tokenize(Iterable<? extends ProgramNode> nodes) {
        for (var node : nodes) {
            tokenize(node);
        }
    }
    
    private void tokenize(ProgramNode node) {
        switch (node) {
            case RootExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS), SemanticTokenTypes.Keyword);
            case StringExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS), SemanticTokenTypes.String);
            case NumberExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS), SemanticTokenTypes.Number);
            case FunctionCallExpression(PropertyAccessExpression function, var args) -> {
                tokenize(function.parent());
                builder.addToken(metadata.get(function, MetadataKey.NAME_POS), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case FunctionCallExpression(VariableAccessExpression function, var args) -> {
                builder.addToken(metadata.get(function, MetadataKey.MAIN_POS), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case PropertyAccessExpression expression -> {
                tokenize(expression.parent());
                builder.addToken(metadata.get(expression, MetadataKey.NAME_POS), SemanticTokenTypes.Property);
            }
            case VariableAccessExpression expression -> {
                var modifiers = new ArrayList<String>();
                String type;
                
                var variable = metadata.get(expression, VariableAnalyser.VARIABLE_REFERENCE).orElse(null);
                if (variable != null) {
                    if (!variable.mutable()) {
                        modifiers.add(SemanticTokenModifiers.Readonly);
                    }
                    if (variable.stdlib()) {
                        modifiers.add(SemanticTokenModifiers.DefaultLibrary);
                    }
                    type = switch (variable.definition()) {
                        case ImportStatement __ -> SemanticTokenTypes.Namespace;
                        case FunctionDeclarationStatement __ -> SemanticTokenTypes.Function;
                        case FunctionArgument __ -> SemanticTokenTypes.Parameter;
                        case Program __ -> {
                            var globalDocData = docHolder.getGlobal(variable.name()).orElse(null);
                            if (globalDocData != null && globalDocData.entry() instanceof DocEntry.GlobalLibraryEntry) {
                                yield SemanticTokenTypes.Namespace;
                            }
                            yield SemanticTokenTypes.Variable;
                        }
                        default -> SemanticTokenTypes.Variable;
                    };
                } else {
                    // Assume unknown variables are locals as that's most likely
                    type = SemanticTokenTypes.Variable;
                }

                builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS), type, modifiers.toArray(new String[0]));
            }
            case ObjectInitializerExpression expression -> {
                for (var entry : expression.contents()) {
                    builder.addToken(metadata.get(entry, MetadataKey.MAIN_POS), SemanticTokenTypes.Property, SemanticTokenModifiers.Declaration);
                    tokenize(entry.value());
                }
            }
            case IsInstanceExpression expression -> {
                tokenize(expression.input());
                builder.addToken(metadata.get(expression, MetadataKey.IS_TYPE_POS), SemanticTokenTypes.Type);
            }
            
            case VariableCreationStatement statement -> {
                var modifiers = statement.mutable() 
                        ? new String[] { SemanticTokenModifiers.Declaration } 
                        : new String[] { SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration };
                builder.addToken(metadata.get(statement, MetadataKey.NAME_POS), SemanticTokenTypes.Variable, modifiers);
                tokenize(statement.initializer());
            }
            case FunctionArgument argument -> {
                builder.addToken(metadata.get(argument, MetadataKey.NAME_POS), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                argument.defaultValue().ifPresent(this::tokenize);
            }
            case FunctionDeclarationStatement statement -> {
                builder.addToken(metadata.get(statement, MetadataKey.NAME_POS), SemanticTokenTypes.Function, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                tokenize(statement.getChildren());
            }
            case ImportStatement statement -> builder.addToken(metadata.get(statement, MetadataKey.NAME_POS), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
            case ForEachLoopStatement statement -> {
                builder.addToken(metadata.get(statement, MetadataKey.NAME_POS), SemanticTokenTypes.Variable, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                tokenize(statement.getChildren());
            }

            case ProgramNode other -> tokenize(other.getChildren());
        }
    } 
    
    private static class DataBuilder {
        private final List<Entry> entries = new ArrayList<>();
        
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        public void addToken(Optional<SourceSpan> span, String type, String... modifiers) {
            span.ifPresent(s -> addToken(s, type, modifiers));
        }
        
        public void addToken(@Nullable SourceSpan span, String type, String... modifiers) {
            if (span == null) return; // it's more convenient to simply pass in null for missing positions than to check it
            entries.add(new Entry(span, type, modifiers));
        }

        public List<Integer> build() {
            var previousRow = 1;
            var previousColumn = 1;
            List<Integer> data = new ArrayList<>();
            entries.sort(Comparator.comparing(entry -> entry.span().from(), Comparator.comparingInt(SourcePos::row).thenComparing(SourcePos::column)));
            
            for (var entry : entries) {
                var span = entry.span();
                
                var row1 = span.from().row();
                var col1 = span.from().column();
                var row2 = span.to().row();
                var col2 = span.to().column();
                if (row1 != row2) {
                    throw new IllegalStateException("Multiline token in data builder");
                }

                var deltaLine = row1 - previousRow;
                var deltaChar = deltaLine == 0 ? col1 - previousColumn : col1 - 1;
                var length = col2 - col1 + 1;
                var typeId = TOKEN_TYPES.get(entry.type);
                var modifierId = Arrays.stream(entry.modifiers).mapToInt(TOKEN_MODIFIERS::get).map(index -> 1 << index).reduce(0, (a, b) -> a | b);

                data.add(deltaLine);
                data.add(deltaChar);
                data.add(length);
                data.add(typeId);
                data.add(modifierId);

                previousRow = row1;
                previousColumn = col1;
            }
            return data;
        }
        
        private record Entry(SourceSpan span, String type, String[] modifiers) {}
    }
}

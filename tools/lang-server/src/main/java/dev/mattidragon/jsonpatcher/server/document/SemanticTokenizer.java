package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.data.DocType;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.FunctionDeclarationStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ImportStatement;
import dev.mattidragon.jsonpatcher.lang.ast.statement.VariableCreationStatement;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class SemanticTokenizer {
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
                SemanticTokenTypes.Variable,
                SemanticTokenTypes.Property,
                
                SemanticTokenTypes.String,
                SemanticTokenTypes.Number
        );
        for (int i = 0; i < supportedTokens.size(); i++) {
            tokenTypes.put(supportedTokens.get(i), i);
        }
        TOKEN_TYPES = Collections.unmodifiableMap(tokenTypes);
        var tokenModifiers = new HashMap<String, Integer>();
        
        var supportedModifiers = List.of(
                SemanticTokenModifiers.Declaration,
                SemanticTokenModifiers.Readonly,
                SemanticTokenModifiers.DefaultLibrary
        );
        for (int i = 0; i < supportedModifiers.size(); i++) {
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

    private void tokenizeDocs(List<NewDocEntry> entries) {
//        for (var entry : entries) {
//            switch (entry) {
//                case DocEntry.Module module -> {
//                    builder.addToken(module.namePos(), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Declaration);
//                    if (module.locationPos() != null) {
//                        var pos = new SourceSpan(module.locationPos().from().offset(-1), module.locationPos().to().offset(1));
//                        builder.addToken(pos, SemanticTokenTypes.String);
//                    }
//                }
//                case DocEntry.Type type -> {
//                    builder.addToken(type.namePos(), SemanticTokenTypes.Type, SemanticTokenModifiers.Declaration);
//                    tokenizeDocType(type.definition());
//                }
//                case DocEntry.Value value -> {
//                    var type = value.definition().isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Property;
//                    builder.addToken(value.namePos(), type, SemanticTokenModifiers.Declaration);
//                    builder.addToken(value.ownerPos(), SemanticTokenTypes.Namespace);
//                    tokenizeDocType(value.definition());
//                }
//                case DocEntry.GlobalModule globalModule -> {
//                    builder.addToken(globalModule.namePos(), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Declaration);
//                }
//                case DocEntry.GlobalValue globalValue -> {
//                    var type = globalValue.definition().isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Property;
//                    builder.addToken(globalValue.namePos(), type, SemanticTokenModifiers.Declaration);
//                    tokenizeDocType(globalValue.definition());
//                }
//            }
//        }
    }
    
    private void tokenizeDocType(DocType type) {
        switch (type) {
            case DocType.Array array -> tokenizeDocType(array.entry());
            case DocType.Function function -> {
                tokenizeDocType(function.returnType());
                for (var arg : function.args()) {
                    tokenizeDocType(arg.type());
                    builder.addToken(arg.namePos(), SemanticTokenTypes.Parameter);
                    for (var pos : arg.operatorPoses()) {
                        builder.addToken(new SourceSpan(pos, pos), SemanticTokenTypes.Operator);
                    }
                }
                for (var pos : function.operatorPoses()) {
                    builder.addToken(pos, SemanticTokenTypes.Operator);
                }
            }
            case DocType.Name name -> builder.addToken(name.pos(), SemanticTokenTypes.Type);
            case DocType.Object object -> tokenizeDocType(object.entry());
            case DocType.Special special -> builder.addToken(special.pos(), SemanticTokenTypes.Type, SemanticTokenModifiers.DefaultLibrary);
            case DocType.Union union -> {
                union.children().forEach(this::tokenizeDocType);
                for (var separator : union.separators()) {
                    builder.addToken(new SourceSpan(separator, separator), SemanticTokenTypes.Operator);
                }
            }
        }
    }

    private void tokenize(Iterable<? extends ProgramNode> nodes) {
        for (var node : nodes) {
            tokenize(node);
        }
    }
    
    private void tokenize(ProgramNode node) {
        switch (node) {
            case RootExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS).orElse(null), SemanticTokenTypes.Keyword);
            case StringExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS).orElse(null), SemanticTokenTypes.String);
            case NumberExpression expression -> builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS).orElse(null), SemanticTokenTypes.Number);
            case FunctionCallExpression(PropertyAccessExpression function, var args) -> {
                tokenize(function.parent());
                builder.addToken(metadata.get(function, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case FunctionCallExpression(VariableAccessExpression function, var args) -> {
                builder.addToken(metadata.get(function, MetadataKey.MAIN_POS).orElse(null), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case PropertyAccessExpression expression -> {
                tokenize(expression.parent());
                builder.addToken(metadata.get(expression, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Property);
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
                            if (globalDocData != null && globalDocData.entry() instanceof NewDocEntry.GlobalLibraryEntry) {
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

                builder.addToken(metadata.get(expression, MetadataKey.MAIN_POS).orElse(null), type, modifiers.toArray(new String[0]));
            }
            case ObjectInitializerExpression expression -> {
                for (var entry : expression.contents()) {
                    builder.addToken(metadata.get(entry, MetadataKey.MAIN_POS).orElse(null), SemanticTokenTypes.Property, SemanticTokenModifiers.Declaration);
                    tokenize(entry.value());
                }
            }
            case IsInstanceExpression expression -> {
                tokenize(expression.input());
                builder.addToken(metadata.get(expression, MetadataKey.IS_TYPE_POS).orElse(null), SemanticTokenTypes.Type);
            }
            
            case VariableCreationStatement statement -> {
                var modifiers = statement.mutable() 
                        ? new String[] { SemanticTokenModifiers.Declaration } 
                        : new String[] { SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration };
                builder.addToken(metadata.get(statement, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Variable, modifiers);
                tokenize(statement.initializer());
            }
            case FunctionArgument argument -> {
                builder.addToken(metadata.get(argument, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                argument.defaultValue().ifPresent(this::tokenize);
            }
            case FunctionDeclarationStatement statement -> {
                builder.addToken(metadata.get(statement, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Function, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                tokenize(statement.getChildren());
            }
            case ImportStatement statement -> builder.addToken(metadata.get(statement, MetadataKey.NAME_POS).orElse(null), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
            
            case ProgramNode other -> tokenize(other.getChildren());
        }
    } 
    
    private static class DataBuilder {
        private final List<Entry> entries = new ArrayList<>();
        
        public void addToken(@Nullable SourceSpan span, String type, String... modifiers) {
            if (span == null) return; // it's more convenient to simply pass in null for missing positions than to check it
            entries.add(new Entry(span, type, modifiers));
        }

        public List<Integer> build() {
            int previousRow = 1;
            int previousColumn = 1;
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

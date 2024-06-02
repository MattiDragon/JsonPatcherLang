package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.data.DocType;
import io.github.mattidragon.jsonpatcher.lang.parse.SourcePos;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArgument;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.FunctionDeclarationStatement;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.VariableCreationStatement;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

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

    private final TreeAnalysis analysis;
    private final DataBuilder builder = new DataBuilder();

    private SemanticTokenizer(TreeAnalysis analysis) {
        this.analysis = analysis;
    }

    public static SemanticTokens getTokens(TreeAnalysis analysis, List<DocEntry> docs) {
        var tokenizer = new SemanticTokenizer(analysis);
        tokenizer.tokenizeDocs(docs);
        tokenizer.tokenize(analysis.getTree());
        return new SemanticTokens(tokenizer.builder.build());
    }

    private void tokenizeDocs(List<DocEntry> entries) {
        for (var entry : entries) {
            switch (entry) {
                case DocEntry.Module module -> {
                    builder.addToken(module.namePos(), SemanticTokenTypes.Namespace, SemanticTokenModifiers.Declaration);
                    if (module.locationPos() != null) {
                        var pos = new SourceSpan(module.locationPos().from().offset(-1), module.locationPos().to().offset(1));
                        builder.addToken(pos, SemanticTokenTypes.String);
                    }
                }
                case DocEntry.Type type -> {
                    builder.addToken(type.namePos(), SemanticTokenTypes.Type, SemanticTokenModifiers.Declaration);
                    tokenizeDocType(type.definition());
                }
                case DocEntry.Value value -> {
                    var type = value.definition().isFunction() ? SemanticTokenTypes.Function : SemanticTokenTypes.Property;
                    builder.addToken(value.namePos(), type, SemanticTokenModifiers.Declaration);
                    builder.addToken(value.ownerPos(), SemanticTokenTypes.Namespace);
                    tokenizeDocType(value.definition());
                }
            }
        }
    }
    
    private void tokenizeDocType(DocType type) {
        switch (type) {
            case DocType.Array array -> tokenizeDocType(array.entry());
            case DocType.Function function -> {
                tokenizeDocType(function.returnType());
                function.args().forEach(arg -> tokenizeDocType(arg.type()));
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
            case RootExpression expression -> builder.addToken(expression.pos(), SemanticTokenTypes.Keyword);
            case ValueExpression(Value.StringValue value, var pos) 
                    -> builder.addToken(pos, SemanticTokenTypes.String);
            case ValueExpression(Value.NumberValue value, var pos) 
                    -> builder.addToken(pos, SemanticTokenTypes.Number);
            case FunctionCallExpression(PropertyAccessExpression function, var args, var funcPos) -> {
                tokenize(function.parent());
                builder.addToken(function.namePos(), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case FunctionCallExpression(VariableAccessExpression function, var args, var funcPos) -> {
                builder.addToken(function.pos(), SemanticTokenTypes.Function);
                tokenize(args);
            }
            case PropertyAccessExpression expression -> {
                tokenize(expression.parent());
                builder.addToken(expression.namePos(), SemanticTokenTypes.Property);
            }
            case VariableAccessExpression expression -> {
                var modifiers = new ArrayList<>();
                String type;
                
                var definition = analysis.getVariableDefinition(expression);
                if (definition != null) {
                    if (!definition.mutable()) {
                        modifiers.add(SemanticTokenModifiers.Readonly);
                    }
                    if (definition.stdlib()) {
                        modifiers.add(SemanticTokenModifiers.DefaultLibrary);
                    }
                    type = switch (definition.kind()) {
                        case IMPORT -> SemanticTokenTypes.Namespace;
                        case FUNCTION -> SemanticTokenTypes.Function;
                        case LOCAL -> SemanticTokenTypes.Variable;
                        case PARAMETER -> SemanticTokenTypes.Parameter;
                    };
                } else {
                    // Assume unknown variables are locals as that's most likely
                    type = SemanticTokenTypes.Variable;
                }

                builder.addToken(expression.pos(), type, modifiers.toArray(new String[0]));
            }
            case ObjectInitializerExpression expression -> {
                for (var entry : expression.contents()) {
                    builder.addToken(entry.namePos(), SemanticTokenTypes.Property, SemanticTokenModifiers.Declaration);
                    tokenize(entry.value());
                }
            }
            
            case VariableCreationStatement statement -> {
                var modifiers = statement.mutable() 
                        ? new String[] { SemanticTokenModifiers.Declaration } 
                        : new String[] { SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration };
                builder.addToken(statement.namePos(), SemanticTokenTypes.Variable, modifiers);
                tokenize(statement.initializer());
            }
            case FunctionArgument argument -> {
                builder.addToken(argument.namePos(), SemanticTokenTypes.Parameter, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                argument.defaultValue().ifPresent(this::tokenize);
            }
            case FunctionDeclarationStatement statement -> {
                builder.addToken(statement.namePos(), SemanticTokenTypes.Function, SemanticTokenModifiers.Readonly, SemanticTokenModifiers.Declaration);
                tokenize(statement.getChildren());
            }
            
            case ProgramNode other -> tokenize(other.getChildren());
        }
    } 
    
    private static class DataBuilder {
        private final List<Entry> entries = new ArrayList<>();
        
        public void addToken(SourceSpan span, String type, String... modifiers) {
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

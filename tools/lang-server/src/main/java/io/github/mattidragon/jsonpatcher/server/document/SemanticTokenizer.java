package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.VariableCreationStatement;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

import java.util.*;

public class SemanticTokenizer {
    private static final List<String> DEFAULT_OBJECTS = List.of("debug", "math", "strings", "arrays", "objects", "functions", "metapatch");
    public static final Map<String, Integer> TOKEN_TYPES;
    public static final Map<String, Integer> TOKEN_MODIFIERS;
    public static final SemanticTokensLegend LEGEND;
    
    static {
        var tokenTypes = new HashMap<String, Integer>();
        var supportedTokens = List.of(
                SemanticTokenTypes.Variable,
                SemanticTokenTypes.Property,
                SemanticTokenTypes.Function,
                SemanticTokenTypes.String,
                SemanticTokenTypes.Number,
                SemanticTokenTypes.Keyword,
                SemanticTokenTypes.Type
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

    private SemanticTokenizer() {}

    public static SemanticTokens getTokens(Program program) {
        var tokenizer = new SemanticTokenizer();
        tokenizer.tokenize(program);
        return new SemanticTokens(tokenizer.builder.getData());
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
                var modifiers = DEFAULT_OBJECTS.contains(expression.name()) ? new String[]{SemanticTokenModifiers.DefaultLibrary} : new String[0];
                builder.addToken(expression.pos(), SemanticTokenTypes.Variable, modifiers);
            }
            case ObjectInitializerExpression expression -> {
                for (var entry : expression.contents()) {
                    builder.addToken(entry.namePos(), SemanticTokenTypes.Property, SemanticTokenModifiers.Declaration);
                    tokenize(entry.value());
                }
            }
            
            case VariableCreationStatement statement -> {
                var modifiers = statement.mutable() ? new String[0] : new String[] { SemanticTokenModifiers.Readonly };
                builder.addToken(statement.namePos(), SemanticTokenTypes.Variable, modifiers);
                tokenize(statement.initializer());
            }
            
            case ProgramNode other -> tokenize(other.getChildren());
        }
    } 
    
    private static class DataBuilder {
        private int previousRow = 1;
        private int previousColumn = 1;
        private final List<Integer> data = new ArrayList<>();
        
        public void addToken(SourceSpan span, String type, String... modifiers) {
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
            var typeId = TOKEN_TYPES.get(type);
            var modifierId = Arrays.stream(modifiers).mapToInt(TOKEN_MODIFIERS::get).map(index -> 1 << index).reduce(0, (a, b) -> a | b);
            
            data.add(deltaLine);
            data.add(deltaChar);
            data.add(length);
            data.add(typeId);
            data.add(modifierId);
            
            previousRow = row1;
            previousColumn = col1;
        }

        public List<Integer> getData() {
            return Collections.unmodifiableList(data);
        }
    }
}

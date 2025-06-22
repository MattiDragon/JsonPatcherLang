package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.Scope;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.Variable;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.PropertyAccessExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class Lookups {
    private final Set<Variable> variables = Collections.newSetFromMap(new IdentityHashMap<>());
    private final PosLookup<PropertyAccessExpression> propertyAccesses = new PosLookup<>();
    private final PosLookup<Scope> scopes = new PosLookup<>();

    private final TreeMetadata metadata;

    private Lookups(TreeMetadata metadata) {
        this.metadata = metadata;
    }

    public static Lookups get(Program program, TreeMetadata metadata) {
        var lookups = new Lookups(metadata);
        lookups.search(program);
        return lookups;
    }

    private void search(ProgramNode node) {
        metadata.get(node, MetadataKey.FULL_POS)
                .ifPresent(pos -> metadata.get(node, VariableAnalyser.SCOPE)
                        .ifPresent(scope -> {
                            scopes.add(pos, scope);
                            variables.addAll(scope.variables());
                        }));

        switch (node) {
            case PropertyAccessExpression expression -> {
                var keywordPos = metadata.get(expression, MetadataKey.KEYWORD_POS);
                var namePos = metadata.get(expression, MetadataKey.NAME_POS);
                if (keywordPos.isPresent() && namePos.isPresent()) {
                    propertyAccesses.add(SourceSpan.between(keywordPos.get(), namePos.get()), expression);
                }
                expression.getChildren().forEach(this::search);
            }
            case ProgramNode other -> other.getChildren().forEach(this::search);
        }
    }

    public Set<Variable> variables() {
        return variables;
    }

    public PosLookup<PropertyAccessExpression> propertyAccesses() {
        return propertyAccesses;
    }

    public PosLookup<Scope> scopes() {
        return scopes;
    }
}

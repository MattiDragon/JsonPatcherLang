package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.server.document.PosLookup;
import dev.mattidragon.jsonpatcher.server.index.symbol.GlobalSymbol;
import dev.mattidragon.jsonpatcher.server.index.symbol.Symbol;
import dev.mattidragon.jsonpatcher.server.index.symbol.VariableSymbol;

import java.util.List;
import java.util.stream.Stream;

public class DocumentIndex {
    private final PosLookup<IndexEntry> entries = new PosLookup<>();

    public void indexVariables(VariableAnalysis analysis, TreeMetadata metadata) {
        for (var scope : analysis.scopes()) {
            for (var variable : scope.variables()) {
                Symbol symbol;
                if (variable.definition() instanceof Program) {
                    symbol = new GlobalSymbol(variable.name());
                } else {
                    symbol = new VariableSymbol(variable);
                }

                var definitionEntry = new IndexEntry(symbol, true);
                var usageEntry = new IndexEntry(symbol, false);

                metadata.get(variable.definition(), MetadataKey.NAME_POS)
                        .ifPresent(pos -> entries.add(pos, definitionEntry));

                for (var usageNode : variable.usages()) {
                    metadata.get(usageNode, MetadataKey.NAME_POS)
                            .ifPresent(pos -> entries.add(pos, usageEntry));
                }
            }
        }
    }

    public Stream<IndexEntry> getEntriesAt(SourcePos pos) {
        return entries.getAllAt(pos);
    }

    public List<SourceSpan> getPositions(IndexEntry entry) {
        return entries.getPositions(entry);
    }
}

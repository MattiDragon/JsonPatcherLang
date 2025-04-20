package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.server.index.Index;

import java.util.List;

public record DocumentData(
        SourceFile sourceFile,
        Program program,
        TreeMetadata treeMetadata,
        List<NewDocEntry> docs,
        Lookups lookups,
        TokenLookup tokens,
        Index index
) {
}

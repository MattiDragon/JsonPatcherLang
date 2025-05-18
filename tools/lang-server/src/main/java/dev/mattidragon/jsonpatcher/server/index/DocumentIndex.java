package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.PatchMetadata;

public class DocumentIndex extends AstIndex {
    public DocumentIndex(String fileName) {
        super(fileName);
    }

    public void index(Program program, PatchMetadata patchMetadata, TreeMetadata metadata, VariableAnalysis variableAnalysis) {
        indexVariables(variableAnalysis, metadata);
        indexMetadata(patchMetadata, metadata);
        indexTree(program, metadata);
    }
}

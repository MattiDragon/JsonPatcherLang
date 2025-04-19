package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalysis;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

public class DocumentIndex extends AstIndex {
    public void index(Program program, TreeMetadata metadata, VariableAnalysis variableAnalysis) {
        indexVariables(variableAnalysis, metadata);
        indexTree(program, metadata);
    }
}

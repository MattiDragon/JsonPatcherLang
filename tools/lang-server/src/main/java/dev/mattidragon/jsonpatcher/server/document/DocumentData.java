package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;

import java.util.List;

public record DocumentData(Program program, TreeMetadata treeMetadata, List<DocEntry> docs, Lookups lookups) {
}

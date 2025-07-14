package dev.mattidragon.jsonpatcher.cli.impl;

import dev.mattidragon.jsonpatcher.docs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.tag.builtin.MethodTagProcessor;
import dev.mattidragon.jsonpatcher.docs.tree.DocTree;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.PrimitiveProperties;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type.Type;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.stdlib.Stdlib;
import dev.mattidragon.jsonpatcher.toolcommon.typing.DocTypeConverter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class PrimitivePropertiesLoader {
    public static final PrimitiveProperties PROPERTIES;

    static {
        var diagnostics = new DiagnosticsBuilder();
        var treeMetadata = new TreeMetadata();

        var commentHandler = new DocCommentHandler(diagnostics, treeMetadata);

        for (var entry : Stdlib.LIBRARY_CONTENTS.entrySet()) {
            Lexer.lex(entry.getValue(), entry.getKey(), diagnostics, commentHandler);
        }

        var docTree = new DocTree(commentHandler.entries());
        var typeConverter = new DocTypeConverter();
        typeConverter.loadTree(docTree);

        var properties = new EnumMap<ValueType, Map<String, Type>>(ValueType.class);

        for (var entry : commentHandler.entries()) {
            if (!(entry instanceof DocEntry.PropertyEntry propertyEntry)) {
                continue;
            }
            treeMetadata.get(entry, MethodTagProcessor.METHOD_TYPE).ifPresent(valueType -> {
                var type = typeConverter.convert(propertyEntry.type());
                properties.computeIfAbsent(valueType, k -> new HashMap<>())
                        .put(propertyEntry.name(), type);
            });
        }

        PROPERTIES = () -> properties;
    }
}

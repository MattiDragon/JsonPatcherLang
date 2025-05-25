package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.analysis.typecheck.PrimitiveProperties;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;

import java.util.Optional;

public interface PrimitivePropertyAccess extends PrimitiveProperties {
    Optional<DocEntry.PropertyEntry> getPrimitivePropertyDocs(ValueType type, String name);
}

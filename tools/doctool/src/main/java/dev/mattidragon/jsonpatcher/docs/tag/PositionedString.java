package dev.mattidragon.jsonpatcher.docs.tag;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

public record PositionedString(String value, SourceSpan pos) {
}

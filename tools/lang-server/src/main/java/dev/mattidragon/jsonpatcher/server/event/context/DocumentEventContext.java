package dev.mattidragon.jsonpatcher.server.event.context;

import dev.mattidragon.jsonpatcher.server.document.DocumentState;

public record DocumentEventContext(DocumentState state) implements EventContext {
}

package dev.mattidragon.jsonpatcher.server.event.document;

import dev.mattidragon.jsonpatcher.server.document.DocumentData;
import dev.mattidragon.jsonpatcher.server.event.DocumentEvent;

public record DocumentDataChangedEvent(DocumentData data) implements DocumentEvent {
}

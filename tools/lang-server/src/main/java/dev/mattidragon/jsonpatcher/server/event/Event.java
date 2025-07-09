package dev.mattidragon.jsonpatcher.server.event;

public sealed interface Event permits DocumentEvent, WorkspaceEvent {
}

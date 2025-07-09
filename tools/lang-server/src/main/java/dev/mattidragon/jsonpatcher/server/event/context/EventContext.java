package dev.mattidragon.jsonpatcher.server.event.context;

public sealed interface EventContext permits WorkspaceEventContext, DocumentEventContext {
}

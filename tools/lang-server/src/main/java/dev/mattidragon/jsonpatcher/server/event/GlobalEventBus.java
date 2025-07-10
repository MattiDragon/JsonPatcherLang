package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.DocumentEventContext;
import dev.mattidragon.jsonpatcher.server.event.context.WorkspaceEventContext;

public class GlobalEventBus {
    private final EventMap<WorkspaceEvent, WorkspaceEventContext> workspaceEvents = new EventMap<>();
    private final EventMap<DocumentEvent, DocumentEventContext> documentEvents = new EventMap<>();

    public <T extends WorkspaceEvent> EventHandlerKey listenWorkspace(Class<T> eventClass, EventHandler<T, WorkspaceEventContext> handler) {
        return workspaceEvents.register(eventClass, handler);
    }

    public <T extends DocumentEvent> EventHandlerKey listenDocument(Class<T> eventClass, EventHandler<T, DocumentEventContext> handler) {
        return documentEvents.register(eventClass, handler);
    }

    void fireWorkspace(WorkspaceEvent event, WorkspaceEventContext context) {
        workspaceEvents.fire(event, context);
    }

    void fireDocument(DocumentEvent event, DocumentEventContext context) {
        documentEvents.fire(event, context);
    }
}

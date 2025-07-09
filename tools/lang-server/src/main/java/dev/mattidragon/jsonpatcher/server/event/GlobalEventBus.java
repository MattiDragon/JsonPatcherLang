package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.DocumentEventContext;
import dev.mattidragon.jsonpatcher.server.event.context.WorkspaceEventContext;

public class GlobalEventBus {
    private final EventMap<WorkspaceEvent, WorkspaceEventContext> workspaceEvents = new EventMap<>();
    private final EventMap<DocumentEvent, DocumentEventContext> documentEvents = new EventMap<>();

    public <T extends WorkspaceEvent> void listenWorkspace(Class<T> eventClass, EventHandler<T, WorkspaceEventContext> handler) {
        workspaceEvents.register(eventClass, handler);
    }

    public <T extends DocumentEvent> void listenDocument(Class<T> eventClass, EventHandler<T, DocumentEventContext> handler) {
        documentEvents.register(eventClass, handler);
    }

    void fireWorkspace(WorkspaceEvent event, WorkspaceEventContext context) {
        workspaceEvents.fire(event, context);
    }

    void fireDocument(DocumentEvent event, DocumentEventContext context) {
        documentEvents.fire(event, context);
    }
}

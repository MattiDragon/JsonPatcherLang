package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.DocumentEventContext;

public class DocumentEventBus {
    private final GlobalEventBus globalBus;
    private final EventMap<DocumentEvent, DocumentEventContext> events = new EventMap<>();
    private final DocumentEventContext context;

    public DocumentEventBus(GlobalEventBus globalBus, DocumentEventContext context) {
        this.globalBus = globalBus;
        this.context = context;
    }

    public <T extends DocumentEvent> void listen(Class<T> eventClass, EventHandler<T, DocumentEventContext> handler) {
        events.register(eventClass, handler);
    }

    public void fire(DocumentEvent event) {
        events.fire(event, context);
        globalBus.fireDocument(event, context);
    }

    public GlobalEventBus globalBus() {
        return globalBus;
    }
}

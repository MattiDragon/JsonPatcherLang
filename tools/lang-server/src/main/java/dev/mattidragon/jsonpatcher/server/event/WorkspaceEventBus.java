package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.WorkspaceEventContext;

public class WorkspaceEventBus {
    private final GlobalEventBus globalBus;
    private final EventMap<WorkspaceEvent, WorkspaceEventContext> events = new EventMap<>();
    private final WorkspaceEventContext context;

    public WorkspaceEventBus(GlobalEventBus globalBus, WorkspaceEventContext context) {
        this.globalBus = globalBus;
        this.context = context;
    }

    public <T extends WorkspaceEvent> EventHandlerKey listen(Class<T> eventClass, EventHandler<T, WorkspaceEventContext> handler) {
        return events.register(eventClass, handler);
    }

    public void fire(WorkspaceEvent event) {
        events.fire(event, context);
        globalBus.fireWorkspace(event, context);
    }

    public GlobalEventBus globalBus() {
        return globalBus;
    }
}

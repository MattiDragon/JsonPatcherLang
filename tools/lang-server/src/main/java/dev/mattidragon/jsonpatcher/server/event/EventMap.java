package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.EventContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class EventMap<T extends Event, C extends EventContext> {
    private final Map<Class<? extends T>, List<EventHandler<? extends T, C>>> handlers = new HashMap<>();

    public synchronized <T2 extends T> void register(Class<T2> eventClass, EventHandler<T2, C> handler) {
        handlers.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(handler);
    }

    // TODO: find a way to not synchronize everything
    @SuppressWarnings("unchecked")
    public synchronized <T2 extends T> void fire(T2 event, C context) {
        var handlers = this.handlers.get(event.getClass());
        if (handlers == null) return;
        for (var handler : handlers) {
            ((EventHandler<T2, C>) handler).handle(event, context);
        }
    }
}

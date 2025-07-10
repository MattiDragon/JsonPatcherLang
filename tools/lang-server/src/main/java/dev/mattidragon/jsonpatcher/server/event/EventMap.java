package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.EventContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class EventMap<T extends Event, C extends EventContext> {
    private final Map<Class<? extends T>, Set<EventHandler<? extends T, C>>> handlers = new HashMap<>();

    public <T2 extends T> EventHandlerKey register(Class<T2> eventClass, EventHandler<T2, C> handler) {
        Set<EventHandler<? extends T, C>> eventHandlers;
        synchronized (this) {
            eventHandlers = handlers.computeIfAbsent(eventClass, k -> new HashSet<>());
        }
        synchronized (eventHandlers) {
            eventHandlers.add(handler);
        }

        return () -> {
            synchronized (eventHandlers) {
                eventHandlers.remove(handler);
            }
        };
    }

    @SuppressWarnings("unchecked")
    public <T2 extends T> void fire(T2 event, C context) {
        Set<EventHandler<T2, C>> handlers;
        synchronized (this) {
            handlers = (Set<EventHandler<T2, C>>) (Set<?>) this.handlers.get(event.getClass());
        }
        if (handlers == null) return;
        synchronized (handlers) {
            for (var handler : handlers) {
                handler.handle(event, context);
            }
        }
    }
}

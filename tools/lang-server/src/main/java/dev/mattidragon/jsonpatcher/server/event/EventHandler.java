package dev.mattidragon.jsonpatcher.server.event;

import dev.mattidragon.jsonpatcher.server.event.context.EventContext;

@FunctionalInterface
public interface EventHandler<E extends Event, C extends EventContext> {
    void handle(E event, C context);
}

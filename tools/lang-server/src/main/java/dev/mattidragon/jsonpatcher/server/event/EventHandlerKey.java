package dev.mattidragon.jsonpatcher.server.event;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface EventHandlerKey {
    void removeHandler();
}

package dev.mattidragon.jsonpatcher.server.event.workspace;

import dev.mattidragon.jsonpatcher.server.event.WorkspaceEvent;
import dev.mattidragon.jsonpatcher.server.index.Index;
import dev.mattidragon.jsonpatcher.server.workspace.BackgroundIndexManager;

/**
 * Fired whenever the {@link BackgroundIndexManager background index manager} updates its index.
 * @param index The index after the update. This index is safe to use across threads,
 *              but its content may change at any point in the future.
 * @param complete {@code true} when the manager has indexed all files provided to it.
 */
public record BackgroundIndexUpdateEvent(Index index, boolean complete) implements WorkspaceEvent {
}

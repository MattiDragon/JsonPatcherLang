package dev.mattidragon.jsonpatcher.server.event.workspace;

import dev.mattidragon.jsonpatcher.server.event.WorkspaceEvent;
import dev.mattidragon.jsonpatcher.server.workspace.config.WorkspaceConfigManager;

import java.util.function.Predicate;

public record WorkspaceConfigChangedEvent(WorkspaceConfigManager manager, Predicate<String> affectedUriPredicate) implements WorkspaceEvent {
}

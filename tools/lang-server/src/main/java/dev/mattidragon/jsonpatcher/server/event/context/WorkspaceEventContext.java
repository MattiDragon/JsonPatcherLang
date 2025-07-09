package dev.mattidragon.jsonpatcher.server.event.context;

import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;

public record WorkspaceEventContext(WorkspaceManager manager) implements EventContext {
}

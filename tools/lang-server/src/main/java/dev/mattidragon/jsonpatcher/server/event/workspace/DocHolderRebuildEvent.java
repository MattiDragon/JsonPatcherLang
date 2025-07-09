package dev.mattidragon.jsonpatcher.server.event.workspace;

import dev.mattidragon.jsonpatcher.server.event.WorkspaceEvent;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;

public record DocHolderRebuildEvent(DocHolder holder) implements WorkspaceEvent {
}

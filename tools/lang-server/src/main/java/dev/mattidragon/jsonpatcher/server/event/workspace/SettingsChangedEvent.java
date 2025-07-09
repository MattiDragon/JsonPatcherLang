package dev.mattidragon.jsonpatcher.server.event.workspace;

import dev.mattidragon.jsonpatcher.server.event.WorkspaceEvent;
import dev.mattidragon.jsonpatcher.server.workspace.settings.Settings;

public record SettingsChangedEvent(Settings oldSettings, Settings newSettings) implements WorkspaceEvent {
}

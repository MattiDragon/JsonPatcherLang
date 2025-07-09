package dev.mattidragon.jsonpatcher.server.workspace.settings;

import com.google.gson.JsonObject;
import dev.mattidragon.jsonpatcher.server.event.WorkspaceEventBus;
import dev.mattidragon.jsonpatcher.server.event.workspace.SettingsChangedEvent;

public class SettingsManager {
    private final WorkspaceEventBus eventBus;
    private Settings settings = Settings.DEFAULT;

    public SettingsManager(WorkspaceEventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void update(JsonObject json) {
        var oldSettings = settings;
        settings = new Settings(json);
        eventBus.fire(new SettingsChangedEvent(oldSettings, settings));
    }

    public Settings settings() {
        return settings;
    }
}

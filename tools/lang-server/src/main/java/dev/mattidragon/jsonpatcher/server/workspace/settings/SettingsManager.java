package dev.mattidragon.jsonpatcher.server.workspace.settings;

import com.google.gson.JsonObject;

public class SettingsManager {
    private Settings settings = Settings.DEFAULT;

    public void update(JsonObject json) {
        settings = new Settings(json);
    }

    public Settings settings() {
        return settings;
    }
}

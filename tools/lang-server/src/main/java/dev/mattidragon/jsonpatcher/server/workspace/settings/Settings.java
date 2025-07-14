package dev.mattidragon.jsonpatcher.server.workspace.settings;

import com.google.gson.JsonObject;

public record Settings(
        boolean formatterEnabled,
        int formatterColumns,
        boolean inlayTypesEnabled
) {
    public static final Settings DEFAULT = new Settings(new JsonObject());

    public Settings(JsonObject json) {
        this(
                booleanProp(json, "formatterEnabled", false),
                intProp(json, "formatterColumns", 120),
                booleanProp(json, "inlayTypesEnabled", true)
        );
    }

    private static boolean booleanProp(JsonObject json, String name, boolean defaultValue) {
        if (!json.has(name)) {
            return defaultValue;
        }
        return json.get(name).getAsBoolean();
    }

    private static int intProp(JsonObject json, String name, int defaultValue) {
        if (!json.has(name)) {
            return defaultValue;
        }
        return json.get(name).getAsInt();
    }
}

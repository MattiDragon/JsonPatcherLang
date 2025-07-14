package dev.mattidragon.jsonpatcher.server.workspace.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record WorkspaceConfig(@Nullable LangVersion version,
                              @Nullable List<String> allowedLibraryGroups,
                              @Nullable Boolean externalStdlibNeeded) {
    public static @Nullable WorkspaceConfig tryParse(JsonObject json) {
        if (!(json.get("schema_version") instanceof JsonPrimitive jsonPrimitive && jsonPrimitive.isNumber()))
            return null;
        if (jsonPrimitive.getAsInt() != 1) return null;

        LangVersion version = null;
        version:
        if (json.get("lang_version") instanceof JsonArray jsonArray && !jsonArray.isEmpty()) {
            int major;
            int minor = 0;
            int patch = 0;
            if (!(jsonArray.get(0) instanceof JsonPrimitive majorPrimitive) || !majorPrimitive.isNumber())
                break version;
            major = majorPrimitive.getAsInt();
            if (jsonArray.size() >= 2) {
                if (!(jsonArray.get(1) instanceof JsonPrimitive minorPrimitive) || !minorPrimitive.isNumber())
                    break version;
                minor = minorPrimitive.getAsInt();
            }
            if (jsonArray.size() >= 3) {
                if (!(jsonArray.get(2) instanceof JsonPrimitive patchPrimitive) || !patchPrimitive.isNumber())
                    break version;
                patch = patchPrimitive.getAsInt();
            }
            version = new LangVersion(major, minor, patch);
        }

        List<String> allowedLibraryGroups = null;
        if (json.get("allowed_library_groups") instanceof JsonArray jsonArray) {
            allowedLibraryGroups = jsonArray.asList()
                    .stream()
                    .filter(JsonPrimitive.class::isInstance)
                    .map(JsonPrimitive.class::cast)
                    .map(JsonPrimitive::getAsString)
                    .toList();
        }

        Boolean externalStdlibNeeded = null;
        if (json.get("external_stdlib_needed") instanceof JsonPrimitive stdlibPrimitive && stdlibPrimitive.isBoolean()) {
            externalStdlibNeeded = stdlibPrimitive.getAsBoolean();
        }

        return new WorkspaceConfig(
                version,
                allowedLibraryGroups,
                externalStdlibNeeded
        );
    }
}

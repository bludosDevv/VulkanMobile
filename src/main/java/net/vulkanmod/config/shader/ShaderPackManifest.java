package net.vulkanmod.config.shader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.vulkanmod.Initializer;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data model + parser for shaderpack manifest file: {@code pack.json}.
 *
 * Expected schema:
 * {
 *   "name": "E-LITE Mobile",
 *   "format": 1,
 *   "description": "optional",
 *   "pipelines": {
 *     "terrain": { "vertex": "pipelines/terrain.vert.spv", "fragment": "pipelines/terrain.frag.spv" }
 *   }
 * }
 */
public record ShaderPackManifest(
        String name,
        int format,
        String description,
        Map<String, PipelineEntry> pipelines
) {
    private static final Gson GSON = new Gson();

    public static ShaderPackManifest load(Path manifestPath) {
        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                throw new IllegalStateException("Manifest root is null");
            }

            String name = getOptionalString(root, "name", "Unnamed Shader Pack");
            int format = getOptionalInt(root, "format", 1);
            String description = getOptionalString(root, "description", "");
            Map<String, PipelineEntry> pipelines = parsePipelines(root);

            if (pipelines.isEmpty()) {
                throw new IllegalStateException("Manifest has no pipeline entries");
            }

            return new ShaderPackManifest(name, format, description, pipelines);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Initializer.LOGGER.error("Failed to load shader pack manifest from {}", manifestPath, e);
            return null;
        }
    }

    public PipelineEntry getPipelineEntry(String pipelineId) {
        if (pipelineId == null || pipelineId.isBlank() || this.pipelines == null) {
            return null;
        }

        return this.pipelines.get(pipelineId);
    }

    private static Map<String, PipelineEntry> parsePipelines(JsonObject root) {
        if (!root.has("pipelines") || !root.get("pipelines").isJsonObject()) {
            return Collections.emptyMap();
        }

        JsonObject pipelinesObject = root.getAsJsonObject("pipelines");
        Map<String, PipelineEntry> map = new LinkedHashMap<>();

        for (Map.Entry<String, JsonElement> entry : pipelinesObject.entrySet()) {
            String pipelineId = entry.getKey();
            JsonElement value = entry.getValue();

            if (pipelineId == null || pipelineId.isBlank() || value == null || !value.isJsonObject()) {
                continue;
            }

            JsonObject pipelineObject = value.getAsJsonObject();
            String vertex = getOptionalString(pipelineObject, "vertex", "");
            String fragment = getOptionalString(pipelineObject, "fragment", "");

            if (vertex.isBlank() || fragment.isBlank()) {
                Initializer.LOGGER.warn("Skipping pipeline '{}' because vertex/fragment path is missing", pipelineId);
                continue;
            }

            map.put(pipelineId, new PipelineEntry(vertex, fragment));
        }

        return Collections.unmodifiableMap(map);
    }

    private static String getOptionalString(JsonObject object, String key, String fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int getOptionalInt(JsonObject object, String key, int fallback) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }

        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return fallback;
        }

        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public record PipelineEntry(String vertex, String fragment) {
    }
}

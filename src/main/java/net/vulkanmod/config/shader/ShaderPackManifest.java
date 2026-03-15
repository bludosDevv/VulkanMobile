package net.vulkanmod.config.shader;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.vulkanmod.Initializer;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record ShaderPackManifest(
        String name,
        int format,
        String description,
        Map<String, PipelineEntry> pipelines
) {
    private static final Gson GSON = new Gson();

    public static ShaderPackManifest load(Path manifestPath) {
        try (Reader reader = Files.newBufferedReader(manifestPath)) {
            ShaderPackManifest manifest = GSON.fromJson(reader, ShaderPackManifest.class);
            if (manifest == null || manifest.pipelines == null) {
                throw new IllegalStateException("Invalid shader pack manifest: missing pipelines");
            }
            return manifest;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Initializer.LOGGER.error("Failed to load shader pack manifest from {}", manifestPath, e);
            return null;
        }
    }

    public PipelineEntry getPipelineEntry(String pipelineId) {
        return this.pipelines.get(pipelineId);
    }

    public record PipelineEntry(
            String vertex,
            String fragment
    ) {}
}

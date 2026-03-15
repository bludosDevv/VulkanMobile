package net.vulkanmod.config.shader;

import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.Initializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ShaderPackManager {
    private static final String SHADER_PACKS_DIR_NAME = "shaderpacks";

    private ShaderPackManager() {}

    public static Path getShaderPacksDir() {
        return FabricLoader.getInstance().getGameDir().resolve(SHADER_PACKS_DIR_NAME);
    }

    public static void ensureShaderPacksDirectory() {
        Path shaderPacksDir = getShaderPacksDir();

        try {
            Files.createDirectories(shaderPacksDir);
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to create shaderpacks directory at {}", shaderPacksDir, e);
        }
    }

    public static List<Path> listShaderPackEntries() {
        ensureShaderPacksDirectory();

        Path shaderPacksDir = getShaderPacksDir();
        try (Stream<Path> stream = Files.list(shaderPacksDir)) {
            return stream.filter(path -> {
                            String name = path.getFileName().toString().toLowerCase();
                            return Files.isDirectory(path) || name.endsWith(".zip");
                        })
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                        .toList();
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to list shaderpacks directory entries from {}", shaderPacksDir, e);
            return List.of();
        }
    }
}

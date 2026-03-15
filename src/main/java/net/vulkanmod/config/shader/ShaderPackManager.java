package net.vulkanmod.config.shader;

import net.fabricmc.loader.api.FabricLoader;
import net.vulkanmod.Initializer;
import net.vulkanmod.vulkan.shader.SPIRVUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ShaderPackManager {
    private static final String SHADER_PACKS_DIR_NAME = "shaderpacks";
    private static final String MANIFEST_FILE_NAME = "pack.json";

    public record PipelineSpirvPair(SPIRVUtils.SPIRV vertex, SPIRVUtils.SPIRV fragment) {}

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
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return Files.isDirectory(path) || name.endsWith(".zip");
                        })
                        .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to list shaderpacks directory entries from {}", shaderPacksDir, e);
            return List.of();
        }
    }

    public static Path getActiveShaderPackDir() {
        ensureShaderPacksDirectory();

        Path shaderPacksDir = getShaderPacksDir();
        String requestedPack = System.getProperty("vulkanmod.shaderpack", "E-LITE");
        Path requestedPackDir = shaderPacksDir.resolve(requestedPack);

        if (Files.isDirectory(requestedPackDir)) {
            return requestedPackDir;
        }

        try (Stream<Path> stream = Files.list(shaderPacksDir)) {
            return stream.filter(Files::isDirectory)
                         .findFirst()
                         .orElse(null);
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to discover active shader pack directory in {}", shaderPacksDir, e);
            return null;
        }
    }

    public static ShaderPackManifest loadActiveManifest() {
        Path packDir = getActiveShaderPackDir();
        if (packDir == null) {
            return null;
        }

        Path manifestPath = packDir.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestPath)) {
            return null;
        }

        return ShaderPackManifest.load(manifestPath);
    }

    public static PipelineSpirvPair loadPipelineSpirvPair(String pipelineId) {
        SPIRVUtils.SPIRV vertex = loadPipelineShaderSpirv(pipelineId, SPIRVUtils.ShaderKind.VERTEX_SHADER);
        SPIRVUtils.SPIRV fragment = loadPipelineShaderSpirv(pipelineId, SPIRVUtils.ShaderKind.FRAGMENT_SHADER);

        if (vertex == null || fragment == null) {
            return null;
        }

        return new PipelineSpirvPair(vertex, fragment);
    }

    public static SPIRVUtils.SPIRV loadPipelineShaderSpirv(String pipelineId, SPIRVUtils.ShaderKind shaderKind) {
        ShaderPackManifest manifest = loadActiveManifest();
        Path packDir = getActiveShaderPackDir();

        if (manifest == null || packDir == null) {
            return null;
        }

        ShaderPackManifest.PipelineEntry entry = manifest.getPipelineEntry(pipelineId);
        if (entry == null) {
            return null;
        }

        String relativePath = switch (shaderKind) {
            case VERTEX_SHADER -> entry.vertex();
            case FRAGMENT_SHADER -> entry.fragment();
            default -> null;
        };

        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }

        Path shaderPath = packDir.resolve(relativePath).normalize();
        if (!shaderPath.startsWith(packDir)) {
            Initializer.LOGGER.warn("Rejected shader path outside shaderpack directory: {}", shaderPath);
            return null;
        }

        if (!Files.isRegularFile(shaderPath)) {
            Initializer.LOGGER.warn("SPIR-V shader file not found: {}", shaderPath);
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(shaderPath);
            ByteBuffer bytecode = ByteBuffer.allocateDirect(bytes.length);
            bytecode.put(bytes).flip();
            return new SPIRVUtils.SPIRV(0L, bytecode);
        } catch (IOException e) {
            Initializer.LOGGER.error("Failed to load SPIR-V shader {}", shaderPath, e);
            return null;
        }
    }
}

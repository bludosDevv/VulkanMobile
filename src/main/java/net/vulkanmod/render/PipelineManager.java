package net.vulkanmod.render;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.vulkanmod.Initializer;
import net.vulkanmod.config.shader.ShaderPackManager;
import net.vulkanmod.render.chunk.build.thread.ThreadBuilderPack;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.render.vertex.TerrainRenderType;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;

import java.util.function.Function;

/**
 * Central pipeline registry/creation point for terrain + core fullscreen pipelines.
 *
 * Shader pack hook point:
 * - createPipeline(...) first tries shaderpack SPIR-V overrides via ShaderPackManager.
 * - when both vertex + fragment SPIR-V are available, it injects them with
 *   pipelineBuilder.setSPIRVs(...), fully bypassing the default GLSL loading path.
 * - otherwise it falls back to built-in shaders via ShaderLoadUtil.loadShaders(...).
 */
public abstract class PipelineManager {
    public static VertexFormat terrainVertexFormat;

    static GraphicsPipeline terrainShader;
    static GraphicsPipeline terrainShaderEarlyZ;
    static GraphicsPipeline fastBlitPipeline;
    static GraphicsPipeline cloudsPipeline;

    private static Function<TerrainRenderType, GraphicsPipeline> shaderGetter;

    private PipelineManager() {
    }

    public static void setTerrainVertexFormat(VertexFormat format) {
        terrainVertexFormat = format;
    }

    public static void init() {
        setTerrainVertexFormat(CustomVertexFormat.COMPRESSED_TERRAIN);
        createBasicPipelines();
        setDefaultShader();
        ThreadBuilderPack.defaultTerrainBuilderConstructor();
    }

    public static void setDefaultShader() {
        setShaderGetter(renderType -> renderType == TerrainRenderType.TRANSLUCENT
                ? terrainShaderEarlyZ
                : terrainShader);
    }

    private static void createBasicPipelines() {
        terrainShaderEarlyZ = createPipeline("terrain_earlyZ", terrainVertexFormat);
        terrainShader = createPipeline("terrain", terrainVertexFormat);
        fastBlitPipeline = createPipeline("blit", CustomVertexFormat.NONE);
        cloudsPipeline = createPipeline("clouds", DefaultVertexFormat.POSITION_COLOR);
    }

    /**
     * Builds a graphics pipeline from JSON state + shader source.
     *
     * Required pipeline state info (blend/depth/raster/descriptors/etc.) is parsed from the
     * standard JSON config via parseBindings(config) exactly like the default path.
     */
    private static GraphicsPipeline createPipeline(String configName, VertexFormat vertexFormat) {
        Pipeline.Builder pipelineBuilder = new Pipeline.Builder(vertexFormat, configName);

        final String shaderRootPath = ShaderLoadUtil.resolveShaderPath("basic");
        JsonObject config = ShaderLoadUtil.getJsonConfig(shaderRootPath, configName);
        pipelineBuilder.parseBindings(config);

        if (!tryApplyShaderPackOverrides(pipelineBuilder, configName)) {
            ShaderLoadUtil.loadShaders(pipelineBuilder, config, configName, shaderRootPath);
        }

        GraphicsPipeline pipeline = pipelineBuilder.createGraphicsPipeline();
        for (var buffer : pipeline.getBuffers()) {
            buffer.setUseGlobalBuffer(true);
        }

        return pipeline;
    }

    /**
     * Hook for Phase 3 SPIR-V pack injection.
     *
     * @return true when override shader modules were injected and default loader should be skipped.
     */
    private static boolean tryApplyShaderPackOverrides(Pipeline.Builder pipelineBuilder, String pipelineId) {
        ShaderPackManager.PipelineSpirvPair spirvPair = ShaderPackManager.loadPipelineSpirvPair(pipelineId);
        if (spirvPair == null) {
            return false;
        }

        if (spirvPair.vertex() == null || spirvPair.fragment() == null) {
            Initializer.LOGGER.warn("Ignoring incomplete SPIR-V override for pipeline '{}'", pipelineId);
            return false;
        }

        pipelineBuilder.setSPIRVs(spirvPair.vertex(), spirvPair.fragment());
        Initializer.LOGGER.info("Using shaderpack SPIR-V override for pipeline '{}'", pipelineId);
        return true;
    }

    public static GraphicsPipeline getTerrainShader(TerrainRenderType renderType) {
        return shaderGetter.apply(renderType);
    }

    public static void setShaderGetter(Function<TerrainRenderType, GraphicsPipeline> consumer) {
        shaderGetter = consumer;
    }

    public static GraphicsPipeline getTerrainDirectShader(RenderType renderType) {
        return terrainShader;
    }

    public static GraphicsPipeline getTerrainIndirectShader(RenderType renderType) {
        return terrainShaderEarlyZ;
    }

    public static GraphicsPipeline getFastBlitPipeline() {
        return fastBlitPipeline;
    }

    public static GraphicsPipeline getCloudsPipeline() {
        return cloudsPipeline;
    }

    public static void destroyPipelines() {
        terrainShaderEarlyZ.cleanUp();
        terrainShader.cleanUp();
        fastBlitPipeline.cleanUp();
        cloudsPipeline.cleanUp();
    }
}

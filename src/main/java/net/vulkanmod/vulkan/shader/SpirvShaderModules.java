package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;

/**
 * Runtime helpers for loading precompiled SPIR-V binaries and creating Vulkan shader modules.
 *
 * This class is intentionally SPIR-V only (no GLSL runtime compile).
 */
public final class SpirvShaderModules {
    private SpirvShaderModules() {
    }

    /**
     * Loads a SPIR-V binary file into a direct {@link ByteBuffer} and wraps it in {@link SPIRVUtils.SPIRV}.
     */
    public static SPIRVUtils.SPIRV loadSpirv(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer bytecode = toDirectSpirvBuffer(bytes);
        return new SPIRVUtils.SPIRV(0L, bytecode);
    }

    /**
     * Convenience overload: loads a file then creates the Vulkan shader module.
     */
    public static long createShaderModule(Path spirvFile) throws IOException {
        SPIRVUtils.SPIRV spirv = loadSpirv(spirvFile);
        return createShaderModule(spirv.bytecode());
    }

    /**
     * Creates a Vulkan shader module from SPIR-V bytecode.
     */
    public static long createShaderModule(ByteBuffer bytecode) {
        if (bytecode == null) {
            throw new IllegalArgumentException("SPIR-V bytecode buffer cannot be null");
        }

        if (!bytecode.isDirect()) {
            throw new IllegalArgumentException("SPIR-V bytecode buffer must be direct");
        }

        if ((bytecode.remaining() & 3) != 0) {
            throw new IllegalArgumentException("SPIR-V bytecode size must be a multiple of 4 bytes");
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(bytecode);

            var pShaderModule = stack.mallocLong(1);
            Vulkan.checkResult(
                    vkCreateShaderModule(Vulkan.getVkDevice(), createInfo, null, pShaderModule),
                    "Failed to create SPIR-V shader module"
            );

            return pShaderModule.get(0);
        }
    }

    private static ByteBuffer toDirectSpirvBuffer(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }
}

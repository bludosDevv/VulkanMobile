package net.vulkanmod.vulkan.shader;

import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;

public final class SpirvShaderModules {
    private SpirvShaderModules() {}

    public static SPIRVUtils.SPIRV loadSpirv(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer bytecode = ByteBuffer.allocateDirect(bytes.length);
        bytecode.put(bytes).flip();
        return new SPIRVUtils.SPIRV(0L, bytecode);
    }

    public static long createShaderModule(ByteBuffer bytecode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(bytecode);

            var pShaderModule = stack.mallocLong(1);
            Vulkan.checkResult(vkCreateShaderModule(Vulkan.getVkDevice(), createInfo, null, pShaderModule),
                               "Failed to create SPIR-V shader module");
            return pShaderModule.get(0);
        }
    }
}

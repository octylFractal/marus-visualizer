/*
 * Copyright (c) Octavia Togami <https://octyl.net>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.octyl.marus.vulkan

import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader
import org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateShaderModule
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import java.nio.ByteBuffer

class Shader(
    private val sourcePath: String,
    private val shader: ByteBuffer,
    private val stage: ShaderStage
) {
    companion object {
        fun compileAndLoadShader(path: String, shaderStage: ShaderStage): Shader {
            val resource = compileGlslToSpirv("shaders/$path", shaderStage)
            return Shader(path, resource, shaderStage)
        }
    }

    fun createVkShaderModule(): Long {
        return closer {
            val stack = pushStack()
            val createInfo = VkShaderModuleCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(shader)
            val shaderModule = stack.mallocLong(1)
            checkedCreate("shader module '$sourcePath' for $stage") {
                vkCreateShaderModule(vkDevice, createInfo, null, shaderModule)
            }
            shaderModule.get()
        }
    }

    fun setupStageInfo(module: Long, stack: MemoryStack = MemoryStack.stackGet()): VkPipelineShaderStageCreateInfo {
        return VkPipelineShaderStageCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
            .stage(stage.vulkan)
            .module(module)
            .pName(stack.UTF8("main"))
    }
}

enum class ShaderStage(
    val vulkan: Int,
    val shaderc: Int
) {
    VERTEX(VK_SHADER_STAGE_VERTEX_BIT, shaderc_vertex_shader),
    FRAGMENT(VK_SHADER_STAGE_FRAGMENT_BIT, shaderc_fragment_shader),
}

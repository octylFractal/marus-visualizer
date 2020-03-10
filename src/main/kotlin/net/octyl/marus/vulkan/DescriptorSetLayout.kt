package net.octyl.marus.vulkan

import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDescriptorSetLayout
import net.octyl.marus.vkDevice
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo

fun createDescriptorSetLayout() {
    closer {
        val stack = pushStack()
        val uboLayoutBinding = VkDescriptorSetLayoutBinding.callocStack(stack)
            .binding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)

        val samplerLayoutBinding = VkDescriptorSetLayoutBinding.callocStack(stack)
            .binding(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)

        val layoutInfo = VkDescriptorSetLayoutCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
            .pBindings(stack.structs(VkDescriptorSetLayoutBinding::mallocStack,
                uboLayoutBinding, samplerLayoutBinding))

        val layout = stack.mallocLong(1)
        checkedCreate({ "descriptor set layout" }) {
            vkCreateDescriptorSetLayout(vkDevice, layoutInfo, null, layout)
        }
        vkDescriptorSetLayout = layout[0]
    }
}

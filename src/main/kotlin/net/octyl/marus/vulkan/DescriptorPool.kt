package net.octyl.marus.vulkan

import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDescriptorPool
import net.octyl.marus.vkDevice
import net.octyl.marus.vkSwapChainImages
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateDescriptorPool
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize

fun createDescriptorPool() {
    closer {
        val stack = pushStack()
        val poolSize = VkDescriptorPoolSize.callocStack(stack)
            .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(vkSwapChainImages.size)
        val poolInfo = VkDescriptorPoolCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
            .pPoolSizes(stack.structs(VkDescriptorPoolSize::mallocStack, poolSize))
            .maxSets(vkSwapChainImages.size)

        val pool = stack.mallocLong(1)
        checkedCreate({ "descriptor pool" }) {
            vkCreateDescriptorPool(vkDevice, poolInfo, null, pool)
        }
        vkDescriptorPool = pool[0]
    }
}

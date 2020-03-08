package net.octyl.marus.vulkan

import net.octyl.marus.data.UniformBufferObject
import net.octyl.marus.util.closer
import net.octyl.marus.util.exportAsDirect
import net.octyl.marus.util.forEach
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.struct.sizeof
import net.octyl.marus.util.structs
import net.octyl.marus.vkDescriptorPool
import net.octyl.marus.vkDescriptorSetLayout
import net.octyl.marus.vkDescriptorSets
import net.octyl.marus.vkDevice
import net.octyl.marus.vkSwapChainImages
import net.octyl.marus.vkUniformBuffers
import org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
import org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets
import org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

fun createDescriptorSets() {
    closer {
        val stack = pushStack()
        val allocInfo = VkDescriptorSetAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(vkDescriptorPool)
            .pSetLayouts(stack.longs(
                *LongArray(vkSwapChainImages.size) { vkDescriptorSetLayout }
            ))

        val sets = stack.mallocLong(allocInfo.descriptorSetCount())
        checkedCreate("descriptor sets") {
            vkAllocateDescriptorSets(vkDevice, allocInfo, sets)
        }

        val bufferInfo = VkDescriptorBufferInfo.callocStack(stack)
            .offset(0)
            .range(sizeof(UniformBufferObject).toLong())
        sets.forEach {
            bufferInfo.buffer(vkUniformBuffers[it])
            val descriptorWrite = VkWriteDescriptorSet.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(sets[it])
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .descriptorCount(1)
                .pBufferInfo(stack.structs(VkDescriptorBufferInfo::mallocStack, bufferInfo))
            val descWrite = stack.structs(VkWriteDescriptorSet::mallocStack, descriptorWrite)

            vkUpdateDescriptorSets(vkDevice, descWrite, null)
        }

        vkDescriptorSets = sets.exportAsDirect()
    }
}

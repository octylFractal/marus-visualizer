package net.octyl.marus.vulkan

import net.octyl.marus.vkDevice
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements

data class ImageHandles(
    val image: Long,
    val memory: Long
)

fun createImage(width: Int, height: Int, format: Int, tiling: Int, usageFlags: Int, properties: Int): ImageHandles {
    MemoryStack.stackPush().use { stack ->
        val imageInfo = VkImageCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .extent {
                it.width(width)
                it.height(height)
                it.depth(1)
            }
            .mipLevels(1)
            .arrayLayers(1)
            .format(format)
            .tiling(tiling)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .usage(usageFlags)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .samples(VK_SAMPLE_COUNT_1_BIT)

        val image = stack.mallocLong(1)
        checkedCreate({ "image" }) {
            vkCreateImage(vkDevice, imageInfo, null, image)
        }

        val memRequirements = VkMemoryRequirements.callocStack(stack)
        vkGetImageMemoryRequirements(vkDevice, image[0], memRequirements)

        val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
            .allocationSize(memRequirements.size())
            .memoryTypeIndex(vkPhysicalDevice.findMemoryType(memRequirements.memoryTypeBits(), properties))

        val memory = stack.mallocLong(1)
        checkedCreate({ "image memory" }) {
            vkAllocateMemory(vkDevice, allocInfo, null, memory)
        }
        vkBindImageMemory(vkDevice, image[0], memory[0], 0)
        return ImageHandles(image[0], memory[0])
    }
}

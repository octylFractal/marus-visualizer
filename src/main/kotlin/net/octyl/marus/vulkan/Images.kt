package net.octyl.marus.vulkan

import net.octyl.marus.util.closerWithStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDevice
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkSamplerCreateInfo

data class ImageHandles(
    val image: Long,
    val memory: Long,
    val format: Int,
    val width: Int,
    val height: Int
) {
    fun destroy(device: VkDevice) {
        vkDestroyImage(device, image, null)
        vkFreeMemory(device, memory, null)
    }
}

fun createImage(width: Int, height: Int, format: Int, tiling: Int, usageFlags: Int, properties: Int): ImageHandles {
    return closerWithStack { stack ->
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
        ImageHandles(image[0], memory[0], format, width, height)
    }
}

fun transitionImageLayout(image: ImageHandles, oldLayout: Int, newLayout: Int) {
    closerWithStack { stack ->
        val commandBuffer = beginSingleUseCmdBuffer()

        val barrier = VkImageMemoryBarrier.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image.image)
            .subresourceRange {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

        val sourceStage: Int
        val destinationStage: Int

        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            barrier.srcAccessMask(0)
            barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)

            sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
            destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT
        } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

            sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
        } else {
            error("unsupported layout transition: $oldLayout to $newLayout")
        }

        vkCmdPipelineBarrier(commandBuffer,
            sourceStage, destinationStage,
            0,
            null,
            null,
            stack.structs(VkImageMemoryBarrier::mallocStack, barrier))

        endSingleUseCmdBuffer(commandBuffer)
    }
}

fun copyBufferToImage(bufferHandles: BufferHandles, image: ImageHandles) {
    closerWithStack { stack ->
        val commandBuffer = beginSingleUseCmdBuffer()

        val region = VkBufferImageCopy.callocStack(stack)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }
            .imageOffset { it.set(0, 0, 0) }
            .imageExtent { it.set(image.width, image.height, 1) }

        vkCmdCopyBufferToImage(commandBuffer, bufferHandles.buffer, image.image,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, stack.structs(VkBufferImageCopy::mallocStack, region))

        endSingleUseCmdBuffer(commandBuffer)
    }
}

fun createImageView(image: ImageHandles) =
    createImageView(image.image, image.format)

fun createImageView(image: Long, format: Int): Long {
    return closerWithStack { stack ->
        val viewInfo = VkImageViewCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format)
            .subresourceRange {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

        val view = stack.mallocLong(1)
        checkedCreate({ "image view" }) {
            vkCreateImageView(vkDevice, viewInfo, null, view)
        }
        view[0]
    }
}

fun createTextureSampler(): Long {
    return closerWithStack { stack ->
        val samplerInfo = VkSamplerCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(VK_FILTER_LINEAR)
            .minFilter(VK_FILTER_LINEAR)
            .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
            .anisotropyEnable(true)
            .maxAnisotropy(16.0f)
            .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
            .mipLodBias(0.0f)
            .minLod(0.0f)
            .maxLod(0.0f)

        val sampler = stack.mallocLong(1)
        checkedCreate({ "texture sampler" }) {
            vkCreateSampler(vkDevice, samplerInfo, null, sampler)
        }
        sampler[0]
    }
}

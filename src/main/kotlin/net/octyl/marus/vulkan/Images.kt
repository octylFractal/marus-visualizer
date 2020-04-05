package net.octyl.marus.vulkan

import net.octyl.marus.util.closerWithStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDevice
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkFormatProperties
import org.lwjgl.vulkan.VkImageBlit
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkSamplerCreateInfo

data class ImageHandles(
    val image: Long,
    val memory: Long,
    val format: Int,
    val width: Int,
    val height: Int,
    val mipLevels: Int
) {
    fun destroy(device: VkDevice) {
        vkDestroyImage(device, image, null)
        vkFreeMemory(device, memory, null)
    }
}

fun createImage(
    width: Int, height: Int, format: Int, tiling: Int, usageFlags: Int, properties: Int,
    mipLevels: Int = 1,
    sampleCount: Int = VK_SAMPLE_COUNT_1_BIT
): ImageHandles {
    return closerWithStack { stack ->
        val imageInfo = VkImageCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
            .imageType(VK_IMAGE_TYPE_2D)
            .extent {
                it.width(width)
                it.height(height)
                it.depth(1)
            }
            .mipLevels(mipLevels)
            .arrayLayers(1)
            .format(format)
            .tiling(tiling)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .usage(usageFlags)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
            .samples(sampleCount)

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
        ImageHandles(image[0], memory[0], format, width, height, mipLevels)
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
                    .levelCount(image.mipLevels)
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

fun createImageView(image: ImageHandles, aspect: Int) =
    createImageView(image.image, image.format, aspect, image.mipLevels)

fun createImageView(image: Long, format: Int, aspect: Int, mipLevels: Int = 0): Long {
    return closerWithStack { stack ->
        val viewInfo = VkImageViewCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_2D)
            .format(format)
            .subresourceRange {
                it.aspectMask(aspect)
                    .baseMipLevel(0)
                    .levelCount(mipLevels)
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

fun createTextureSampler(image: ImageHandles): Long {
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
            .maxLod(image.mipLevels.toFloat())

        val sampler = stack.mallocLong(1)
        checkedCreate({ "texture sampler" }) {
            vkCreateSampler(vkDevice, samplerInfo, null, sampler)
        }
        sampler[0]
    }
}

fun generateMipmaps(image: ImageHandles) {
    closerWithStack { stack ->
        val formatProperties = VkFormatProperties.callocStack(stack)
        vkGetPhysicalDeviceFormatProperties(vkPhysicalDevice, image.format, formatProperties)

        check(formatProperties.optimalTilingFeatures() and VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT != 0) {
            "Unable to generate mipmaps for texture type ${image.format.to10AndHexString()}"
        }

        val commandBuffer = beginSingleUseCmdBuffer()

        val barrier = VkImageMemoryBarrier.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .image(image.image)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .subresourceRange {
                it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseArrayLayer(0)
                    .layerCount(1)
                    .levelCount(1)
            }

        var width = image.width
        var height = image.height

        for (i in 1 until image.mipLevels) {
            val scaledWidth = (width / 2).coerceAtLeast(1)
            val scaledHeight = (height / 2).coerceAtLeast(1)
            barrier.subresourceRange { it.baseMipLevel(i - 1) }
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)

            vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0,
                null, null, stack.structs(VkImageMemoryBarrier::mallocStack, barrier))

            val blit = VkImageBlit.callocStack()
                .srcOffsets(0) {
                    it.set(0, 0, 0)
                }
                .srcOffsets(1) {
                    it.set(width, height, 1)
                }
                .srcSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(i - 1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
                .dstOffsets(0) {
                    it.set(0, 0, 0)
                }
                .dstOffsets(1) {
                    it.set(scaledWidth, scaledHeight, 1)
                }
                .dstSubresource {
                    it.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .mipLevel(i)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
            vkCmdBlitImage(commandBuffer,
                image.image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                image.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                stack.structs(VkImageBlit::mallocStack, blit),
                VK_FILTER_LINEAR)

            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

            vkCmdPipelineBarrier(commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
                null, null, stack.structs(VkImageMemoryBarrier::mallocStack, barrier))

            width = scaledWidth
            height = scaledHeight
        }

        barrier.subresourceRange { it.baseMipLevel(image.mipLevels - 1) }
            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
        vkCmdPipelineBarrier(commandBuffer,
            VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0,
            null, null, stack.structs(VkImageMemoryBarrier::mallocStack, barrier))

        endSingleUseCmdBuffer(commandBuffer)
    }
}

fun VkPhysicalDevice.findSupportedFormat(candidates: IntArray, tiling: Int, features: Int): Int {
    return closerWithStack { stack ->
        for (candidate in candidates) {
            val props = VkFormatProperties.callocStack(stack)
            vkGetPhysicalDeviceFormatProperties(this@findSupportedFormat, candidate, props)

            if (tiling == VK_IMAGE_TILING_LINEAR && (props.linearTilingFeatures() and features) == features) {
                return@closerWithStack candidate
            } else if (tiling == VK_IMAGE_TILING_OPTIMAL && (props.optimalTilingFeatures() and features) == features) {
                return@closerWithStack candidate
            }
        }

        error("failed to find a supported format from ${candidates.contentToString()}")
    }
}

fun VkPhysicalDevice.findDepthFormat(): Int = findSupportedFormat(
    intArrayOf(VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT),
    tiling = VK_IMAGE_TILING_OPTIMAL,
    features = VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT
)

fun hasStencilComponent(format: Int): Boolean {
    return when (format) {
        VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT -> true
        else -> false
    }
}

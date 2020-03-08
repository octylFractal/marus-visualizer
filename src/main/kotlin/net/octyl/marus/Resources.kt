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

package net.octyl.marus

import net.octyl.marus.util.byteBuffer
import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.vulkan.ImageHandles
import net.octyl.marus.vulkan.createBuffer
import net.octyl.marus.vulkan.createImage
import org.lwjgl.stb.STBImage.STBI_rgb_alpha
import org.lwjgl.stb.STBImage.stbi_load_from_memory
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB
import org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT
import org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
import org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import java.io.InputStream

object Resources {
    /**
     * Retrieve the resource, relative to this class.
     */
    fun getResource(path: String): InputStream {
        return javaClass.getResourceAsStream(path)
    }

    fun getImage(path: String): ImageHandles {
        return closer {
            val stack = pushStack()
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            val channels = stack.mallocInt(1)
            val encodedData = byteBuffer { getResource(path) }
            val rawData = stbi_load_from_memory(encodedData, width, height, channels, STBI_rgb_alpha)
            check(rawData != null) {
                "Failed to load image from $path"
            }
            check(channels[0] == 4) {
                "Image from $path did not load with 4 channels"
            }

            val size = (width[0] * height[0] * channels[0]).toLong()
            val handles = createBuffer(stack,
                size = size,
                bufferFlags = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
                memoryFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
            handles.copyFrom(vkDevice, memAddress(rawData))

            createImage(width[0], height[0],
                format = VK_FORMAT_R8G8B8A8_SRGB,
                tiling = VK_IMAGE_TILING_OPTIMAL,
                usageFlags = VK_IMAGE_USAGE_TRANSFER_DST_BIT or VK_IMAGE_USAGE_SAMPLED_BIT,
                properties = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
        }
    }
}

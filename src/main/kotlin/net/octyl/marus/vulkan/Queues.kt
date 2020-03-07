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
import net.octyl.marus.util.listAllElements
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkSurface
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT
import org.lwjgl.vulkan.VK10.VK_TRUE
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkQueueFamilyProperties

class Queue(
    val index: Int,
    var queueHandle: VkQueue? = null
) {
    fun createInfo(stack: MemoryStack): VkDeviceQueueCreateInfo {
        return VkDeviceQueueCreateInfo.callocStack(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
            .queueFamilyIndex(index)
            .pQueuePriorities(stack.floats(1.0f))
    }
}

data class Queues(
    val graphicsQueue: Queue? = null,
    val presentQueue: Queue? = null
) {
    val allQueues by lazy { listOfNotNull(graphicsQueue, presentQueue) }
    val isComplete by lazy { allQueues.size == 2 }
}

fun VkPhysicalDevice.findQueues(): Queues {
    return closer {
        val stack = pushStack()
        val queuesBuffer = listAllElements(VkQueueFamilyProperties::mallocStack, stack) { count, output ->
            vkGetPhysicalDeviceQueueFamilyProperties(this@findQueues, count, output)
        }
        var queues = Queues()
        for ((index, queue) in queuesBuffer.withIndex()) {
            if (queues.isComplete) {
                break
            }

            val queueObject = Queue(index)

            if (queues.graphicsQueue == null && (queue.queueFlags() and VK_QUEUE_GRAPHICS_BIT) != 0) {
                queues = queues.copy(graphicsQueue = queueObject)
            }
            if (queues.presentQueue == null) {
                val isSupported = stack.mallocInt(1)
                checkedGet("surface support") {
                    vkGetPhysicalDeviceSurfaceSupportKHR(this@findQueues, index, vkSurface, isSupported)
                }
                if (isSupported.get() == VK_TRUE) {
                    queues = queues.copy(presentQueue = queueObject)
                }
            }
        }
        queues
    }
}

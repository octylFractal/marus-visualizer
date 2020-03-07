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

import mu.KotlinLogging
import net.octyl.marus.util.asSequence
import net.octyl.marus.util.closer
import net.octyl.marus.util.listAllElements
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkInstance
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_CPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_OTHER
import org.lwjgl.vulkan.VK10.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU
import org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties
import org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFeatures
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties
import org.lwjgl.vulkan.VkExtensionProperties
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkPhysicalDeviceProperties
import java.nio.ByteBuffer

private val LOGGER = KotlinLogging.logger { }

fun pickPhysicalDevice(): VkPhysicalDevice {
    return closer {
        val stack = pushStack()
        val physicalDevices = listAllElements({ count, stk -> stk.mallocPointer(count) }, stack) { count, output ->
            checkedAction("enumerate physical devices") {
                vkEnumeratePhysicalDevices(vkInstance, count, output)
            }
        }
        check(physicalDevices.hasRemaining()) {
            "No Vulkan-supporting devices found!"
        }
        physicalDevices.asSequence { this[it] }
            .map { VkPhysicalDevice(it, vkInstance) }
            .map { device -> device to scoreDevice(device) }
            .filter { it.second > 0L }
            .sortedBy { it.second }
            .map { it.first }
            .firstOrNull() ?: throw IllegalStateException("No suitable device found!")
    }
}

private fun scoreDevice(device: VkPhysicalDevice): Long {
    return closer {
        val stack = pushStack()
        val deviceProperties = VkPhysicalDeviceProperties.callocStack(stack)
        val deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack)
        vkGetPhysicalDeviceProperties(device, deviceProperties)
        vkGetPhysicalDeviceFeatures(device, deviceFeatures)

        fun notifySkipReason(reason: String) {
            LOGGER.info("Not considering ${deviceProperties.deviceNameString()} since it $reason")
        }

        var score = 0L

        score += when (deviceProperties.deviceType()) {
            VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU -> 10_000
            VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU -> 1_000
            VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU, VK_PHYSICAL_DEVICE_TYPE_CPU -> 100
            VK_PHYSICAL_DEVICE_TYPE_OTHER -> 10
            else -> 0
        }

        val queues = device.findQueues()

        if (!queues.isComplete) {
            notifySkipReason("does not support all queues")
            return@closer 0
        }

        if (!checkExtensionSupport(device)) {
            notifySkipReason("does not support all extensions")
            return@closer 0
        }

        if (querySwapChainSupport(device)?.takeIf { it.isComplete } == null) {
            notifySkipReason("does not support the swap chain")
            return@closer 0
        }
        return@closer score
    }
}

private fun checkExtensionSupport(device: VkPhysicalDevice): Boolean {
    return closer {
        val stack = pushStack()
        val extensions = listAllElements(VkExtensionProperties::mallocStack, stack) { count, output ->
            vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, count, output)
        }

        extensions.map { it.extensionNameString() }.toSet()
            .containsAll(listOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME))
    }
}

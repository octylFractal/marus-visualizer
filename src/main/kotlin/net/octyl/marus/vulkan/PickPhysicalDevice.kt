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
import org.lwjgl.vulkan.VK10.*
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
            checkedAction({ "enumerate physical devices" }) {
                vkEnumeratePhysicalDevices(vkInstance, count, output)
            }
        }
        check(physicalDevices.hasRemaining()) {
            "No Vulkan-supporting devices found!"
        }
        val result = physicalDevices.asSequence { this[it] }
            .map { VkPhysicalDevice(it, vkInstance) }
            .mapNotNull { device -> scoreDevice(device) }
            .onEach { LOGGER.info { "Considering ${it.name}: score ${it.score}" } }
            .sortedBy { -it.score }
            .firstOrNull() ?: throw IllegalStateException("No suitable device found!")
        LOGGER.info {"Using physical device: ${result.name}" }
        msaaSamples = result.sampleCount
        result.physicalDevice
    }
}

private data class ScoredDevice(
    val name: String,
    val physicalDevice: VkPhysicalDevice,
    val score: Long,
    val sampleCount: Int
)

private fun scoreDevice(device: VkPhysicalDevice): ScoredDevice? {
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
            return@closer null
        }

        if (!checkExtensionSupport(device)) {
            notifySkipReason("does not support all extensions")
            return@closer null
        }

        if (querySwapChainSupport(device)?.takeIf { it.isComplete } == null) {
            notifySkipReason("does not support the swap chain")
            return@closer null
        }
        if (!deviceFeatures.samplerAnisotropy()) {
            notifySkipReason("does not support sampler anisotropy")
            return@closer null
        }
        return@closer score.takeUnless { it == 0L }?.let {
            ScoredDevice(
                deviceProperties.deviceNameString(),
                device, score, getMaxUsableSampleCount(deviceProperties)
            )
        }
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

private val SAMPLE_COUNTS = intArrayOf(
    VK_SAMPLE_COUNT_64_BIT,
    VK_SAMPLE_COUNT_32_BIT,
    VK_SAMPLE_COUNT_16_BIT,
    VK_SAMPLE_COUNT_8_BIT,
    VK_SAMPLE_COUNT_4_BIT,
    VK_SAMPLE_COUNT_2_BIT,
    VK_SAMPLE_COUNT_1_BIT
)

private fun getMaxUsableSampleCount(physicalDeviceProperties: VkPhysicalDeviceProperties): Int {
    val limits = physicalDeviceProperties.limits()
    val counts = limits.framebufferColorSampleCounts() and
        limits.framebufferDepthSampleCounts()

    return SAMPLE_COUNTS.first {
        (counts and it) != 0
    }
}

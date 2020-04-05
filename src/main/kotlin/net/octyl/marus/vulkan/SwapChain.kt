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
import net.octyl.marus.util.VkColorSpace
import net.octyl.marus.util.VkFormatName
import net.octyl.marus.util.asSequence
import net.octyl.marus.util.closer
import net.octyl.marus.util.listAllElements
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkColorImage
import net.octyl.marus.vkColorImageView
import net.octyl.marus.vkCommandBuffers
import net.octyl.marus.vkCommandPool
import net.octyl.marus.vkDepthImage
import net.octyl.marus.vkDepthImageView
import net.octyl.marus.vkDescriptorPool
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageViews
import net.octyl.marus.vkPipeline
import net.octyl.marus.vkPipelineLayout
import net.octyl.marus.vkRenderPass
import net.octyl.marus.vkSurface
import net.octyl.marus.vkSwapChain
import net.octyl.marus.vkSwapChainFramebuffers
import net.octyl.marus.vkSwapChainImages
import net.octyl.marus.vkUniformBuffers
import net.octyl.marus.window
import org.lwjgl.glfw.GLFW.glfwGetFramebufferSize
import org.lwjgl.glfw.GLFW.glfwWaitEvents
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
import org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
import org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR
import org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR
import org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR
import org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR
import org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR
import org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR
import org.lwjgl.vulkan.VkSurfaceFormatKHR
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR

private val LOGGER = KotlinLogging.logger { }

data class SwapChainDetails(
    val capabilities: VkSurfaceCapabilitiesKHR,
    val formats: List<VkSurfaceFormatKHR>,
    val presentModes: Set<Int>
) {
    val isComplete = formats.isNotEmpty() && presentModes.isNotEmpty()
    fun pickBestFormat(): VkSurfaceFormatKHR {
        // first meeting conditions, or just first available
        val selected = formats.firstOrNull {
            it.format() == VK_FORMAT_B8G8R8A8_SRGB &&
                it.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR
        } ?: formats.first()
        LOGGER.info { "Picked format: ${selected.toReadableString()}" }
        return selected
    }

    private fun VkSurfaceFormatKHR.toReadableString() =
        "Format: ${VkFormatName[format()]}, Color Space: ${VkColorSpace[colorSpace()]}"

    fun pickBestPresentMode(): Int {
        // easiest mode to work with
        return presentModes.first { it == VK_PRESENT_MODE_FIFO_KHR }
    }

    /**
     * Pick the best swap extent. The output is in the [vkExtent2D] parameter.
     * The width and height already present in [vkExtent2D] will be used as a
     * desired goal.
     */
    fun pickSwapExtent(vkExtent2D: VkExtent2D) {
        // -1 is UINT32_MAX as an int32 :)
        if (capabilities.currentExtent().width() != -1) {
            vkExtent2D.set(capabilities.currentExtent())
            return
        }
        val min = capabilities.minImageExtent()
        val max = capabilities.maxImageExtent()
        vkExtent2D.width(
            vkExtent2D.width().coerceIn(min.width(), max.height())
        )
        vkExtent2D.height(
            vkExtent2D.height().coerceIn(min.height(), max.height())
        )
    }
}

fun querySwapChainSupport(device: VkPhysicalDevice): SwapChainDetails? {
    return closer {
        val stack = pushStack()
        val capabilitiesStack = VkSurfaceCapabilitiesKHR.callocStack(stack)
        LOGGER.logFailure({ "get surface capabilities" }) {
            vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, vkSurface, capabilitiesStack)
        }?.let { return@closer null }
        // save for return if success -- we do to stack first for easy cleanup
        val capabilities = VkSurfaceCapabilitiesKHR.create().also {
            memCopy(capabilitiesStack, it)
        }

        val formatsBuffer = listAllElements(VkSurfaceFormatKHR::mallocStack, stack) { count, output ->
            LOGGER.logFailure({ "get surface formats" }) {
                vkGetPhysicalDeviceSurfaceFormatsKHR(device, vkSurface, count, output)
            }?.let { return@closer null }
        }
        // copy them to GC'able memory
        val formats = formatsBuffer.map { stackLocalFormat ->
            VkSurfaceFormatKHR.create().also { heapFormat ->
                memCopy(stackLocalFormat, heapFormat)
            }
        }

        val presentModesBuffer = listAllElements({ count, stk -> stk.mallocInt(count) }, stack) { count, output ->
            LOGGER.logFailure({ "surface present modes" }) {
                vkGetPhysicalDeviceSurfacePresentModesKHR(device, vkSurface, count, output)
            }?.let { return@closer null }
        }
        val presentModes = presentModesBuffer.asSequence { this[it] }.toSet()

        SwapChainDetails(capabilities, formats, presentModes)
    }
}

fun recreateSwapChain() {
    waitForRestore()

    vkDeviceWaitIdle(vkDevice)

    cleanupSwapChain()

    createSwapChain()
    createImageViews()
    createRenderPass()
    createGraphicsPipeline()
    createFramebuffer()
    createUniformBuffers()
    createDescriptorPool()
    createDescriptorSets()
    createCommandBuffers()
}

fun waitForRestore() {
    closer {
        val stack = pushStack()
        val width = stack.mallocInt(1)
        val height = stack.mallocInt(1)
        glfwGetFramebufferSize(window, width, height)
        while (width[0] == 0 || height[0] == 0) {
            glfwGetFramebufferSize(window, width, height)
            glfwWaitEvents()
        }
    }
}

fun cleanupSwapChain() {
    for (framebuffer in vkSwapChainFramebuffers) {
        vkDestroyFramebuffer(vkDevice, framebuffer, null)
    }

    vkFreeCommandBuffers(vkDevice, vkCommandPool, vkCommandBuffers)

    vkDestroyPipeline(vkDevice, vkPipeline, null)
    vkDestroyPipelineLayout(vkDevice, vkPipelineLayout, null)
    vkDestroyRenderPass(vkDevice, vkRenderPass, null)

    for (image in vkImageViews) {
        vkDestroyImageView(vkDevice, image, null)
    }

    vkDepthImage.destroy(vkDevice)
    vkDestroyImageView(vkDevice, vkDepthImageView, null)

    vkColorImage.destroy(vkDevice)
    vkDestroyImageView(vkDevice, vkColorImageView, null)

    vkDestroySwapchainKHR(vkDevice, vkSwapChain, null)

    vkUniformBuffers.forEach { it.destroy(vkDevice) }

    vkDestroyDescriptorPool(vkDevice, vkDescriptorPool, null)
}

fun createSwapChain() {
    closer {
        val stack = pushStack()
        val details = querySwapChainSupport(vkPhysicalDevice)!!

        val format = details.pickBestFormat()
        val presentMode = details.pickBestPresentMode()

        val imageCount = (details.capabilities.minImageCount() + 1).let { imageCount ->
            when (val maxImageCount = details.capabilities.maxImageCount()) {
                0 -> imageCount
                else -> imageCount.coerceAtMost(maxImageCount)
            }
        }

        val createInfo = VkSwapchainCreateInfoKHR.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
            .surface(vkSurface)
            .minImageCount(imageCount)
            .imageFormat(format.format())
            .imageColorSpace(format.colorSpace())
            .imageArrayLayers(1)
            .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)

        val queues = queues.allQueues.map { it.index }.distinct()

        if (queues.size == 2) {
            createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT)
                .pQueueFamilyIndices(stack.ints(*queues.toIntArray()))
        } else {
            createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
        }

        createInfo.preTransform(details.capabilities.currentTransform())
            .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            .presentMode(presentMode)
            .clipped(true)
            .oldSwapchain(MemoryUtil.NULL)

        // sometimes we get into a race condition where the window has resized again
        // try to avoid this by requerying immediately before creation
        // https://github.com/KhronosGroup/Vulkan-Docs/issues/1144

        val newDetails = querySwapChainSupport(vkPhysicalDevice)!!
        val extent = VkExtent2D.callocStack(stack)
        glfwGetFramebufferSize(window,
            MemoryUtil.memIntBuffer(extent.address() + VkExtent2D.WIDTH, 1),
            MemoryUtil.memIntBuffer(extent.address() + VkExtent2D.HEIGHT, 1)
        )
        newDetails.pickSwapExtent(extent)
        createInfo.imageExtent(extent)

        val swapChainBuffer = stack.mallocLong(1)
        checkedCreate({ "swap chain" }) {
            vkCreateSwapchainKHR(vkDevice, createInfo, null, swapChainBuffer)
        }
        vkSwapChain = swapChainBuffer.get()

        val swapImageBuffer = listAllElements({ count, stk -> stk.mallocLong(count) }) { count, output ->
            checkedGet({ "swap-chain images" }) {
                vkGetSwapchainImagesKHR(vkDevice, vkSwapChain, count, output)
            }
        }
        vkSwapChainImages = swapImageBuffer.let {
            val array = LongArray(it.remaining())
            it.get(array)
            array
        }

        vkSwapChainFormat = format.format()
        // copy off of stack, into GC'able holder
        vkSwapChainExtent = VkExtent2D.create().set(extent)
    }
}

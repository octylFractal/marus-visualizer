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

import mu.KotlinLogging
import net.octyl.marus.util.forEach
import net.octyl.marus.vulkan.cleanupSwapChain
import net.octyl.marus.vulkan.drawFrame
import net.octyl.marus.vulkan.initVulkan
import org.lwjgl.PointerBuffer
import org.lwjgl.Version
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.vulkan.EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT
import org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR
import org.lwjgl.vulkan.VK10.vkDestroyCommandPool
import org.lwjgl.vulkan.VK10.vkDestroyDevice
import org.lwjgl.vulkan.VK10.vkDestroyFence
import org.lwjgl.vulkan.VK10.vkDestroyInstance
import org.lwjgl.vulkan.VK10.vkDestroySemaphore
import org.lwjgl.vulkan.VK10.vkDeviceWaitIdle
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkInstance
import java.nio.LongBuffer

private val LOGGER = KotlinLogging.logger { }
var window = NULL
var vkDebugCallback = NULL
lateinit var vkInstance: VkInstance
lateinit var vkDevice: VkDevice
var vkSurface = NULL
var vkSwapChain = NULL
lateinit var vkSwapChainImages: LongArray
lateinit var vkImageViews: LongArray
var vkPipelineLayout = NULL
var vkRenderPass = NULL
var vkPipeline = NULL
lateinit var vkSwapChainFramebuffers: LongArray
var vkCommandPool = NULL
lateinit var vkCommandBuffers: PointerBuffer
lateinit var vkImageAvailableSemaphores: LongBuffer
lateinit var vkRenderFinishedSemaphores: LongBuffer
lateinit var vkInflightFences: LongBuffer
lateinit var vkImagesInFlight: LongBuffer
val DEBUG = System.getProperty("marus.debug")?.toBoolean() == true
const val MAX_FRAMES_IN_FLIGHT = 2

var swapChainOutdated = false

fun main() {
    LOGGER.info { "LWJGL: ${Version.getVersion()}" }
    initWindow()
    initVulkan()
    mainLoop()
    cleanup()
}

private fun initWindow() {
    GLFWErrorCallback.createThrow().set()
    glfwInit()

    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    window = glfwCreateWindow(800, 600, "Test window!", NULL, NULL)

    glfwSetFramebufferSizeCallback(window) { _, _, _ ->
        swapChainOutdated = true
    }
}

private fun mainLoop() {
    while (!glfwWindowShouldClose(window)) {
        glfwWaitEvents()
        drawFrame()
    }
    vkDeviceWaitIdle(vkDevice)
}

private fun cleanup() {
    if (::vkDevice.isInitialized) {
        if (::vkImageAvailableSemaphores.isInitialized) {
            vkImageAvailableSemaphores.forEach {
                vkDestroySemaphore(vkDevice, get(it), null)
            }
        }
        if (::vkRenderFinishedSemaphores.isInitialized) {
            vkRenderFinishedSemaphores.forEach {
                vkDestroySemaphore(vkDevice, get(it), null)
            }
        }
        if (::vkInflightFences.isInitialized) {
            vkInflightFences.forEach {
                vkDestroyFence(vkDevice, get(it), null)
            }
        }
        cleanupSwapChain()
        vkDestroyCommandPool(vkDevice, vkCommandPool, null)
        vkDestroyDevice(vkDevice, null)
    }
    if (vkSurface != NULL) {
        vkDestroySurfaceKHR(vkInstance, vkSurface, null)
    }
    if (vkDebugCallback != NULL) {
        vkDestroyDebugUtilsMessengerEXT(vkInstance, vkDebugCallback, null)
    }
    vkDestroyInstance(vkInstance, null)
    glfwDestroyWindow(window)
    glfwTerminate()
}

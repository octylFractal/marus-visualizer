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
import net.octyl.marus.DEBUG
import net.octyl.marus.Resources
import net.octyl.marus.data.obj.Importer
import net.octyl.marus.util.closer
import net.octyl.marus.util.forEach
import net.octyl.marus.util.listAllElements
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDebugCallback
import net.octyl.marus.vkDepthImage
import net.octyl.marus.vkDepthImageView
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageViews
import net.octyl.marus.vkInstance
import net.octyl.marus.vkRectImage
import net.octyl.marus.vkRectImageView
import net.octyl.marus.vkRectSampler
import net.octyl.marus.vkSwapChainImages
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.system.MemoryUtil.memASCII
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
import org.lwjgl.vulkan.EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME
import org.lwjgl.vulkan.EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT
import org.lwjgl.vulkan.EXTDebugUtils.vkCreateDebugUtilsMessengerEXT
import org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1
import org.lwjgl.vulkan.VkApplicationInfo
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkDeviceCreateInfo
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo
import org.lwjgl.vulkan.VkExtent2D
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkInstanceCreateInfo
import org.lwjgl.vulkan.VkLayerProperties
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures
import org.lwjgl.vulkan.VkQueue
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

private val LOGGER = KotlinLogging.logger {}

lateinit var vkPhysicalDevice: VkPhysicalDevice
var vkSwapChainFormat by Delegates.notNull<Int>()
lateinit var vkSwapChainExtent: VkExtent2D
lateinit var queues: Queues
var vkDepthFormat by Delegates.notNull<Int>()

fun initVulkan() {
    createInstance()
    setupDebugMessenger()
    createSurface()
    vkPhysicalDevice = pickPhysicalDevice()
    queues = vkPhysicalDevice.findQueues()
    createLogicalDevice()
    createSwapChain()
    createImageViews()
    createRenderPass()
    createDescriptorSetLayout()
    createGraphicsPipeline()
    createFramebuffer()
    createCommandPool()

    vkRectImage = Resources.getImage("textures/chalet.jpg")
    vkRectImageView = createImageView(vkRectImage, aspect = VK_IMAGE_ASPECT_COLOR_BIT)
    vkRectSampler = createTextureSampler()

    createVertexBuffer()
    createIndexBuffer()
    createUniformBuffers()
    createDescriptorPool()
    createDescriptorSets()
    createCommandBuffers()
    createSyncObjects()
}

private const val NEW_VALIDATION_LAYER = "VK_LAYER_KHRONOS_validation"
private const val LEGACY_VALIDATION_LAYER = "VK_LAYER_LUNARG_standard_validation"

private fun createInstance() {
    closer {
        val stack = pushStack()
        val appInfo = VkApplicationInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
            .pApplicationName(stack.UTF8("Hello Triangle"))
            .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
            .pEngineName(stack.UTF8("No Engine"))
            .engineVersion(VK_MAKE_VERSION(1, 0, 0))
            .apiVersion(VK_API_VERSION_1_1)
        val createInfo = VkInstanceCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
            .pApplicationInfo(appInfo)

        if (DEBUG) {
            val validationLayer = listOf(NEW_VALIDATION_LAYER, LEGACY_VALIDATION_LAYER)
                .firstOrNull { checkValidationLayerSupport(it) }
                ?: error("Validation layers required!")
            LOGGER.info("Validation layers detected ($validationLayer)! Enabling them for Vulkan.")
            createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8(validationLayer)))
        }

        val glfwExtensions = glfwGetRequiredInstanceExtensions()!!
        val additionalElements = sequence {
            if (DEBUG) {
                yield(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
            }
        }.toList()
        val newBuffer = stack.mallocPointer(glfwExtensions.remaining() + additionalElements.size)
        newBuffer.put(glfwExtensions)
        additionalElements.forEach { newBuffer.put(stack.ASCII(it)) }
        newBuffer.flip()
        LOGGER.info("Using Vulkan extensions:")
        newBuffer.forEach {
            LOGGER.info("\t- ${memASCII(get(it))}")
        }
        createInfo.ppEnabledExtensionNames(newBuffer)

        vkInstance = stack.mallocPointer(1).let { buffer ->
            checkedCreate({ "Vulkan instance" }) {
                vkCreateInstance(createInfo, null, buffer)
            }
            VkInstance(buffer.get(), createInfo)
        }
    }
}

private fun checkValidationLayerSupport(layer: String): Boolean {
    return closer {
        val stack = pushStack()
        val layerPropsBuffer = listAllElements(VkLayerProperties::mallocStack, stack) { count, output ->
            vkEnumerateInstanceLayerProperties(count, output)
        }
        layerPropsBuffer.asSequence()
            .map { it.layerNameString() }
            .any { it == layer }
    }
}

private fun setupDebugMessenger() {
    if (!DEBUG) {
        return
    }
    closer {
        val stack = pushStack()
        val callbackLogger = LoggerFactory.getLogger("VulkanDebugUtils")
        val createInfo = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
            .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
            .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT or
                VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
            .pfnUserCallback { messageSeverity, _, pCallbackData, _ ->
                val data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData)
                val loggerCallback: (String, Throwable) -> Unit = when {
                    messageSeverity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT ->
                        callbackLogger::error
                    messageSeverity >= VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT ->
                        callbackLogger::warn
                    else -> callbackLogger::info
                }
                loggerCallback(data.pMessageString(), RuntimeException("Stacktrace"))
                VK_FALSE
            }

        val messenger = stack.mallocLong(1)
        checkedCreate({ "debug messenger" }) {
            vkCreateDebugUtilsMessengerEXT(vkInstance, createInfo, null, messenger)
        }
        vkDebugCallback = messenger.get()
    }
}

private fun createLogicalDevice() {
    closer {
        val stack = pushStack()
        val queuesList = queues.allQueues
        val uniqueQueues = queuesList.toSet()
        val queueCreateInfos = stack.structs(VkDeviceQueueCreateInfo::mallocStack,
            *uniqueQueues.map { it.createInfo(stack) }.toTypedArray()
        )
        val deviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack)
            .samplerAnisotropy(true)
        val deviceCreateInfo = VkDeviceCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
            .pQueueCreateInfos(queueCreateInfos)
            .pEnabledFeatures(deviceFeatures)
            .ppEnabledExtensionNames(stack.pointers(
                stack.ASCII(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
            ))

        val logicalDevice = stack.mallocPointer(1)
        checkedCreate({ "logical device" }) {
            vkCreateDevice(vkPhysicalDevice, deviceCreateInfo, null, logicalDevice)
        }
        vkDevice = VkDevice(logicalDevice.get(), vkPhysicalDevice, deviceCreateInfo)
        for (queue in uniqueQueues) {
            val queueHandle = stack.mallocPointer(1)
            vkGetDeviceQueue(vkDevice, queue.index, 0, queueHandle)
            queue.queueHandle = VkQueue(queueHandle.get(), vkDevice)
        }
    }
}

fun createImageViews() {
    vkImageViews = LongArray(vkSwapChainImages.size) {
        createImageView(vkSwapChainImages[it], vkSwapChainFormat, aspect = VK_IMAGE_ASPECT_COLOR_BIT)
    }

    createDepthResources()
}

private fun createDepthResources() {
    vkDepthFormat = vkPhysicalDevice.findDepthFormat()
    vkDepthImage = createImage(vkSwapChainExtent.width(), vkSwapChainExtent.height(),
        format = vkDepthFormat,
        tiling = VK_IMAGE_TILING_OPTIMAL,
        usageFlags = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
        properties = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
    vkDepthImageView = createImageView(vkDepthImage, aspect = VK_IMAGE_ASPECT_DEPTH_BIT)
}

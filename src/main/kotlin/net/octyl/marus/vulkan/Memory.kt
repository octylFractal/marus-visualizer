package net.octyl.marus.vulkan

import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties

fun VkPhysicalDevice.findMemoryType(typeFilter: Int, properties: Int): Int {
    return closer {
        val stack = pushStack()
        val memProperties = VkPhysicalDeviceMemoryProperties.callocStack(stack)
        vkGetPhysicalDeviceMemoryProperties(this@findMemoryType, memProperties)

        for (i in 0 until memProperties.memoryTypeCount()) {
            if (typeFilter and (1 shl i) != 0 &&
                (memProperties.memoryTypes(i).propertyFlags() and properties) == properties) {
                return@closer i
            }
        }

        error("Failed to find suitable memory type")
    }
}

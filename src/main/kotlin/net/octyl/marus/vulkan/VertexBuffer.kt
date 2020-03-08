package net.octyl.marus.vulkan

import net.octyl.marus.INDICIES
import net.octyl.marus.VERTICES
import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.struct.memByteBuffer
import net.octyl.marus.util.structs
import net.octyl.marus.vkCommandPool
import net.octyl.marus.vkDevice
import net.octyl.marus.vkIndexBuffer
import net.octyl.marus.vkIndexBufferMemory
import net.octyl.marus.vkVertexBuffer
import net.octyl.marus.vkVertexBufferMemory
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkSubmitInfo
import java.nio.ByteBuffer
import java.nio.LongBuffer

fun createVertexBuffer() {
    closer {
        val stack = pushStack()
        val buffer = stack.mallocLong(1)
        val memory = stack.mallocLong(1)

        uploadData(stack, memByteBuffer(VERTICES), buffer, memory, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)

        vkVertexBuffer = buffer[0]
        vkVertexBufferMemory = memory[0]
    }
}

fun createIndexBuffer() {
    closer {
        val stack = pushStack()
        val buffer = stack.mallocLong(1)
        val memory = stack.mallocLong(1)

        uploadData(stack, INDICIES, buffer, memory, VK_BUFFER_USAGE_INDEX_BUFFER_BIT)

        vkIndexBuffer = buffer[0]
        vkIndexBufferMemory = memory[0]
    }
}

private fun uploadData(stack: MemoryStack,
                       data: ByteBuffer,
                       buffer: LongBuffer,
                       memory: LongBuffer,
                       bufferUsageFlag: Int) {
    val stagingBuffer = stack.mallocLong(1)
    val stagingMemory = stack.mallocLong(1)
    val size = data.remaining().toLong()

    createBuffer(stack,
        size = size,
        bufferFlags = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
        memoryFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
        buffer = stagingBuffer,
        memory = stagingMemory)

    val pointerCapture = stack.mallocPointer(1)
    vkMapMemory(vkDevice, stagingMemory[0], 0, size, 0, pointerCapture)
    memCopy(memAddress(data), pointerCapture[0], size)
    vkUnmapMemory(vkDevice, stagingMemory[0])

    createBuffer(stack,
        size = size,
        bufferFlags = VK_BUFFER_USAGE_TRANSFER_DST_BIT or bufferUsageFlag,
        memoryFlags = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
        buffer = buffer,
        memory = memory)

    copyBuffer(stagingBuffer[0], buffer[0], size)

    vkDestroyBuffer(vkDevice, stagingBuffer[0], null)
    vkFreeMemory(vkDevice, stagingMemory[0], null)
}

private fun createBuffer(stack: MemoryStack,
                         size: Long,
                         bufferFlags: Int,
                         memoryFlags: Int,
                         buffer: LongBuffer,
                         memory: LongBuffer) {
    val bufferInfo = VkBufferCreateInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
        .size(size)
        .usage(bufferFlags)
        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

    checkedCreate("buffer") {
        vkCreateBuffer(vkDevice, bufferInfo, null, buffer)
    }

    val memoryRequirements = VkMemoryRequirements.callocStack(stack)
    vkGetBufferMemoryRequirements(vkDevice, buffer[0], memoryRequirements)

    val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
        .allocationSize(memoryRequirements.size())
        .memoryTypeIndex(vkPhysicalDevice.findMemoryType(memoryRequirements.memoryTypeBits(),
            memoryFlags))

    checkedCreate("buffer memory") {
        vkAllocateMemory(vkDevice, allocInfo, null, memory)
    }

    vkBindBufferMemory(vkDevice, buffer[0], memory[0], 0)
}

private fun copyBuffer(source: Long, dest: Long, size: Long) {
    closer {
        val stack = pushStack()
        val allocInfo = VkCommandBufferAllocateInfo.callocStack()
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandPool(vkCommandPool)
            .commandBufferCount(1)

        val buffer = stack.mallocPointer(1)
        checkedCreate("copy command buffer") {
            vkAllocateCommandBuffers(vkDevice, allocInfo, buffer)
        }
        val commandBuffer = VkCommandBuffer(buffer[0], vkDevice)

        val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
            .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)
        vkBeginCommandBuffer(commandBuffer, beginInfo)

        val copy = VkBufferCopy.callocStack(stack)
            .size(size)
        vkCmdCopyBuffer(commandBuffer, source, dest, stack.structs(VkBufferCopy::mallocStack, copy))

        vkEndCommandBuffer(commandBuffer)

        val submitInfo = VkSubmitInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
            .pCommandBuffers(buffer)
        val queueHandle = queues.graphicsQueue!!.queueHandle!!
        vkQueueSubmit(queueHandle, submitInfo, VK_NULL_HANDLE)
        vkQueueWaitIdle(queueHandle)
        vkFreeCommandBuffers(vkDevice, vkCommandPool, buffer)
    }
}

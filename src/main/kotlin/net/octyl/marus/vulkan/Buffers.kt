package net.octyl.marus.vulkan

import net.octyl.marus.INDICIES
import net.octyl.marus.VERTICES
import net.octyl.marus.data.UniformBufferObject
import net.octyl.marus.util.closer
import net.octyl.marus.util.closerWithStack
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.struct.memByteBuffer
import net.octyl.marus.util.struct.sizeof
import net.octyl.marus.util.structs
import net.octyl.marus.vkDevice
import net.octyl.marus.vkIndexBuffer
import net.octyl.marus.vkSwapChainImages
import net.octyl.marus.vkUniformBuffers
import net.octyl.marus.vkVertexBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memByteBuffer
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkBufferCopy
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import java.nio.ByteBuffer

fun createVertexBuffer() {
    vkVertexBuffer = uploadData(memByteBuffer(VERTICES), VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
}

fun createIndexBuffer() {
    vkIndexBuffer = uploadData(memByteBuffer(INDICIES), VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
}

fun createUniformBuffers() {
    closer {
        val stack = pushStack()

        vkUniformBuffers = vkSwapChainImages.indices.map {
            createBuffer(stack,
                size = sizeof(UniformBufferObject).toLong(),
                bufferFlags = VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT,
                memoryFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)
        }
    }
}

data class BufferHandles(
    val buffer: Long,
    val memory: Long,
    val size: Long
) {
    fun copyFrom(device: VkDevice, address: Long) {
        closerWithStack { stack ->
            val data = stack.mallocPointer(1)
            vkMapMemory(device, memory, 0, size, 0, data)
            memCopy(address, data[0], size)
            vkUnmapMemory(device, memory)
        }
    }

    fun destroy(device: VkDevice) {
        vkDestroyBuffer(device, buffer, null)
        vkFreeMemory(device, memory, null)
    }
}

private fun uploadData(data: ByteBuffer,
                       bufferUsageFlag: Int): BufferHandles {
    return closerWithStack { stack ->
        val size = data.remaining().toLong()

        val staging = createBuffer(stack,
            size = size,
            bufferFlags = VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            memoryFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)

        staging.copyFrom(vkDevice, memAddress(data))

        val output = createBuffer(stack,
            size = size,
            bufferFlags = VK_BUFFER_USAGE_TRANSFER_DST_BIT or bufferUsageFlag,
            memoryFlags = VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)

        copyBuffer(staging.buffer, output.buffer, size)

        staging.destroy(vkDevice)

        output
    }
}

fun createBuffer(stack: MemoryStack,
                 size: Long,
                 bufferFlags: Int,
                 memoryFlags: Int): BufferHandles {
    val bufferInfo = VkBufferCreateInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
        .size(size)
        .usage(bufferFlags)
        .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
    val buffer = stack.mallocLong(1)
    val memory = stack.mallocLong(1)

    checkedCreate({ "buffer" }) {
        vkCreateBuffer(vkDevice, bufferInfo, null, buffer)
    }

    val memoryRequirements = VkMemoryRequirements.callocStack(stack)
    vkGetBufferMemoryRequirements(vkDevice, buffer[0], memoryRequirements)

    val allocInfo = VkMemoryAllocateInfo.callocStack(stack)
        .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
        .allocationSize(memoryRequirements.size())
        .memoryTypeIndex(vkPhysicalDevice.findMemoryType(memoryRequirements.memoryTypeBits(),
            memoryFlags))

    checkedCreate({ "buffer memory" }) {
        vkAllocateMemory(vkDevice, allocInfo, null, memory)
    }

    vkBindBufferMemory(vkDevice, buffer[0], memory[0], 0)

    return BufferHandles(buffer[0], memory[0], size)
}

private fun copyBuffer(source: Long, dest: Long, size: Long) {
    closer {
        val stack = pushStack()
        val commandBuffer = beginSingleUseCmdBuffer()

        val copy = VkBufferCopy.callocStack(stack)
            .size(size)
        vkCmdCopyBuffer(commandBuffer, source, dest, stack.structs(VkBufferCopy::mallocStack, copy))

        endSingleUseCmdBuffer(commandBuffer)
    }
}

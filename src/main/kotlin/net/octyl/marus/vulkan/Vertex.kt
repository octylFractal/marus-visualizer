package net.octyl.marus.vulkan

import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.ByteBuffer

data class Vertex(
    val position: Vector2f,
    val color: Vector3f
) {
    companion object {
        const val SIZEOF = java.lang.Float.BYTES * 5
        const val POSITION = java.lang.Float.BYTES * 0
        const val COLOR = java.lang.Float.BYTES * 2

        val bindingDescription: VkVertexInputBindingDescription =
            VkVertexInputBindingDescription.create()
                .binding(0)
                .stride(SIZEOF)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        val attributeDescription: VkVertexInputAttributeDescription.Buffer =
            VkVertexInputAttributeDescription.create(2)
                .apply(0) {
                    // position first
                    it.binding(0)
                        .location(0)
                        .format(VK_FORMAT_R32G32_SFLOAT)
                        .offset(POSITION)
                }
                .apply(1) {
                    // color next
                    it.binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(COLOR)
                }
    }

    fun get(index: Int, buffer: ByteBuffer) {
        position.get(index + POSITION, buffer)
        color.get(index + COLOR, buffer)
    }
}

package net.octyl.marus.data

import net.octyl.marus.util.struct.Layout
import net.octyl.marus.util.struct.Member
import net.octyl.marus.util.struct.MvStruct
import net.octyl.marus.util.struct.MvStructType
import net.octyl.marus.util.struct.offsetIn
import net.octyl.marus.util.struct.sizeof
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT
import org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import java.nio.ByteBuffer

class Vertex(container: ByteBuffer) : MvStruct<Vertex>(container, Vertex) {
    companion object : MvStructType<Vertex> {
        val POSITION = Member("position", Vec3f)
        val COLOR = Member("color", Vec3f)
        val TEXTURE = Member("texture", Vec2f)

        override val layout = Layout(POSITION, COLOR, TEXTURE)

        override fun create(container: ByteBuffer) = Vertex(container)

        val bindingDescription: VkVertexInputBindingDescription =
            VkVertexInputBindingDescription.create()
                .binding(0)
                .stride(sizeof(this))
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)

        val attributeDescription: VkVertexInputAttributeDescription.Buffer =
            VkVertexInputAttributeDescription.create(3)
                .apply(0) {
                    // position first
                    it.binding(0)
                        .location(0)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(POSITION.offsetIn(layout))
                }
                .apply(1) {
                    // color next
                    it.binding(0)
                        .location(1)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(COLOR.offsetIn(layout))
                }
                .apply(2) {
                    // texture next
                    it.binding(0)
                        .location(2)
                        .format(VK_FORMAT_R32G32_SFLOAT)
                        .offset(TEXTURE.offsetIn(layout))
                }
    }

    fun position() = POSITION.get()

    fun position(value: Vec3f) = POSITION.set(value)

    fun position(x: Float, y: Float, z: Float) = apply {
        // this writes through to our buffer
        position().set(x, y, z)
    }

    fun color() = COLOR.get()

    fun color(value: Vec3f) = COLOR.set(value)

    fun color(x: Float, y: Float, z: Float) = apply {
        // this writes through to our buffer
        color().set(x, y, z)
    }

    fun texture() = TEXTURE.get()

    fun texture(value: Vec2f) = TEXTURE.set(value)

    fun texture(x: Float, y: Float) = apply {
        // this writes through to our buffer
        texture().set(x, y)
    }
}

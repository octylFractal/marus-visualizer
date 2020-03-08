package net.octyl.marus.data

import net.octyl.marus.util.struct.Layout
import net.octyl.marus.util.struct.Member
import net.octyl.marus.util.struct.MvStruct
import net.octyl.marus.util.struct.MvStructType
import java.nio.ByteBuffer

class UniformBufferObject(container: ByteBuffer) : MvStruct<UniformBufferObject>(container, UniformBufferObject) {
    companion object : MvStructType<UniformBufferObject> {
        val MODEL = Member("model", Mat4f)
        val VIEW = Member("view", Mat4f)
        val PROJ = Member("proj", Mat4f)

        override val layout = Layout(MODEL, VIEW, PROJ)

        override fun create(container: ByteBuffer) = UniformBufferObject(container)
    }

    fun model() = MODEL.get()

    fun model(value: Mat4f) = MODEL.set(value)

    fun view() = VIEW.get()

    fun view(value: Mat4f) = VIEW.set(value)

    fun proj() = PROJ.get()

    fun proj(value: Mat4f) = PROJ.set(value)
}

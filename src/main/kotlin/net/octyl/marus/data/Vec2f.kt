package net.octyl.marus.data

import net.octyl.marus.util.struct.Layout
import net.octyl.marus.util.struct.Member
import net.octyl.marus.util.struct.MemberType
import net.octyl.marus.util.struct.MvStruct
import net.octyl.marus.util.struct.MvStructType
import net.octyl.marus.util.struct.floatMember
import java.nio.ByteBuffer

class Vec2f(container: ByteBuffer) : MvStruct<Vec2f>(container, Vec2f) {
    companion object : MvStructType<Vec2f> {
        val X = floatMember("x")
        val Y = floatMember("y")

        override val layout = Layout(X, Y)

        override fun create(container: ByteBuffer) = Vec2f(container)
    }

    fun set(x: Float, y: Float) = x(x).y(y)

    fun x() = X.get()

    fun x(value: Float) = X.set(value)

    fun y() = Y.get()

    fun y(value: Float) = Y.set(value)
}

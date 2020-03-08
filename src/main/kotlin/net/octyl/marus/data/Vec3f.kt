package net.octyl.marus.data

import net.octyl.marus.util.struct.Layout
import net.octyl.marus.util.struct.MvStruct
import net.octyl.marus.util.struct.MvStructType
import net.octyl.marus.util.struct.floatMember
import java.nio.ByteBuffer

class Vec3f(container: ByteBuffer) : MvStruct<Vec3f>(container, Vec3f) {
    companion object : MvStructType<Vec3f> {
        val X = floatMember("x")
        val Y = floatMember("y")
        val Z = floatMember("z")

        override val layout = Layout(X, Y, Z)

        override fun create(container: ByteBuffer) = Vec3f(container)
    }

    fun set(x: Float, y: Float, z: Float) = x(x).y(y).z(z)

    fun x() = X.get()

    fun x(value: Float) = X.set(value)

    fun y() = Y.get()

    fun y(value: Float) = Y.set(value)

    fun z() = Z.get()

    fun z(value: Float) = Z.set(value)
}

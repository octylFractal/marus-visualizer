package net.octyl.marus.data

import net.octyl.marus.util.struct.Layout
import net.octyl.marus.util.struct.MvStruct
import net.octyl.marus.util.struct.MvStructType
import net.octyl.marus.util.struct.floatMember
import org.joml.Matrix4f
import java.nio.ByteBuffer

class Mat4f(container: ByteBuffer) : MvStruct<Mat4f>(container, Mat4f) {
    companion object : MvStructType<Mat4f> {
        val M = (0..3)
            // the flipped a/b here is important
            // Matrix4f is column-major
            .flatMap { b ->
                (0..3).map { a ->
                    a to b
                }
            }
            .map { (a, b) -> floatMember("m$a$b") }

        override val layout = Layout(M)

        override fun create(container: ByteBuffer) = Mat4f(container)
    }

    fun m(x: Int, y: Int) = M[x + y * 4].get()

    fun m(x: Int, y: Int, value: Float) = M[x + y * 4].set(value)

}

fun Matrix4f.copyTo(mat4f: Mat4f) {
    get(mat4f.container)
}

fun Mat4f.copyTo(matrix4f: Matrix4f) {
    matrix4f.set(container)
}

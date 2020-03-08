package net.octyl.marus.util.struct

import org.lwjgl.system.MemoryUtil.memGetFloat
import org.lwjgl.system.MemoryUtil.memPutFloat

interface MemberType<T> {

    val bytes: Int

    fun read(address: Long, offset: Int): T

    fun write(address: Long, offset: Int, value: T)

    abstract class KnownSize<T>(override val bytes: Int) : MemberType<T>

    object Float : KnownSize<kotlin.Float>(4) {
        override fun read(address: Long, offset: Int) =
            memGetFloat(address + offset)
        override fun write(address: Long, offset: Int, value: kotlin.Float) =
            memPutFloat(address + offset, value)
    }
}

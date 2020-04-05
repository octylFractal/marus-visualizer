package net.octyl.marus.util

import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer

interface Allocator {
    fun calloc(size: Int): ByteBuffer

    fun malloc(size: Int): ByteBuffer = calloc(size)

    fun free(byteBuffer: ByteBuffer)

    fun realloc(byteBuffer: ByteBuffer, size: Int): ByteBuffer =
        malloc(size).put(byteBuffer.flip()).also {
            free(byteBuffer)
        }
}

enum class StdAllocator : Allocator {
    /**
     * Memory allocated by this allocator is managed by the JVM, and will be freed by GC.
     */
    JVM_MANAGED {
        override fun calloc(size: Int): ByteBuffer = BufferUtils.createByteBuffer(size)
        override fun free(byteBuffer: ByteBuffer) {}
    },

    /**
     * Memory allocated by this allocator is managed by the application, and must be explicitly freed.
     */
    APP_MANAGED {
        override fun calloc(size: Int): ByteBuffer = MemoryUtil.memCalloc(size)
        override fun malloc(size: Int): ByteBuffer = MemoryUtil.memAlloc(size)
        override fun free(byteBuffer: ByteBuffer) = MemoryUtil.memFree(byteBuffer)
        override fun realloc(byteBuffer: ByteBuffer, size: Int): ByteBuffer = MemoryUtil.memRealloc(byteBuffer, size)
    },

    /**
     * Memory allocated by this allocator is managed by the [MemoryStack] class, and will be freed
     * when the stack is popped.
     */
    STACK {
        override fun calloc(size: Int): ByteBuffer = MemoryStack.stackCalloc(size)
        override fun malloc(size: Int): ByteBuffer = MemoryStack.stackMalloc(size)
        override fun free(byteBuffer: ByteBuffer) {}
    }
}

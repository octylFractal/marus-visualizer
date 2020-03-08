package net.octyl.marus.util.struct

import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memAlloc
import org.lwjgl.system.MemoryUtil.memByteBuffer
import org.lwjgl.system.MemoryUtil.memCalloc
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.system.MemoryUtil.nmemAllocChecked
import org.lwjgl.system.MemoryUtil.nmemCallocChecked
import java.nio.ByteBuffer

/**
 * Base struct-like helper. Comparable to LWJGL's struct system, but geared towards manual writing,
 * at the cost of runtime speed.
 */
abstract class MvStruct<SELF : MvStruct<SELF>>(
    internal val container: ByteBuffer,
    val type: MvStructType<SELF>
) {
    // we assume the generic is correct
    @Suppress("UNCHECKED_CAST")
    private fun asSelf() = this as SELF

    fun <T> Member<T>.get() = container.get(this, this@MvStruct.type.layout)

    fun <T> Member<T>.set(value: T): SELF = asSelf().apply {
        container.put(this@set, type.layout, value)
    }
}

class MvStructBuffer<S : MvStruct<S>>(
    val address: Long,
    private val remaining: Long,
    // this is only to keep the reference for GC
    @Suppress("UNUSED")
    private val container: ByteBuffer?,
    val type: MvStructType<S>
) {
    private val singleSizeOf = sizeof(type)

    init {
        require(remaining % singleSizeOf == 0L) {
            "Container size (${remaining}) is not divisible by struct size ($singleSizeOf)"
        }
    }

    fun remaining() = remaining / singleSizeOf

    fun get(index: Int): S = type.read(address, index * singleSizeOf)

    fun put(index: Int, struct: S) = apply {
        type.write(address, index * singleSizeOf, struct)
    }
}

fun memByteBuffer(buffer: MvStructBuffer<*>): ByteBuffer =
    memByteBuffer(buffer.address, buffer.remaining().toInt() * sizeof(buffer.type))

interface MvStructType<T : MvStruct<T>> : MemberType<T> {

    override val bytes: Int
        get() = layout.size

    override fun read(address: Long, offset: Int): T {
        return create(memByteBuffer(address + offset, bytes))
    }

    override fun write(address: Long, offset: Int, value: T) {
        val source = value.container
        memCopy(memAddress(source), address + offset, source.remaining().toLong())
    }

    val layout: Layout

    fun create(container: ByteBuffer): T

    /**
     * Create an instance on the heap, managed by GC.
     */
    fun create() = create(BufferUtils.createByteBuffer(bytes))

    /**
     * Create an instance on the heap that must be explicitly freed.
     */
    fun malloc() = create(memAlloc(bytes))

    /**
     * Create an instance on the heap that must be explicitly freed.
     */
    fun calloc() = create(memCalloc(bytes))

    /**
     * Create an instance on the stack.
     */
    fun mallocStack(stack: MemoryStack = MemoryStack.stackGet()) = create(stack.malloc(bytes))

    /**
     * Create an instance on the stack.
     */
    fun callocStack(stack: MemoryStack = MemoryStack.stackGet()) = create(stack.calloc(bytes))

    // Buffers:

    private fun createBuffer(address: Long, remaining: Long, container: ByteBuffer? = null) =
        MvStructBuffer(address, remaining, container, this)

    private fun createBuffer(container: ByteBuffer) =
        createBuffer(memAddress(container), container.remaining().toLong(), container)

    /**
     * Create a buffer of instances on the heap, managed by GC.
     */
    fun create(capacity: Int) = createBuffer(BufferUtils.createByteBuffer(bytes * capacity))

    /**
     * Create a buffer of instances on the heap that must be explicitly freed.
     */
    fun malloc(capacity: Int) = createBuffer(
        nmemAllocChecked((bytes * capacity).toLong()),
        (bytes * capacity).toLong()
    )

    /**
     * Create a buffer of instances on the heap that must be explicitly freed.
     */
    fun calloc(capacity: Int) = createBuffer(
        nmemCallocChecked(capacity.toLong(), bytes.toLong()),
        (bytes * capacity).toLong()
    )

    /**
     * Create a buffer of instances on the stack.
     */
    fun mallocStack(capacity: Int, stack: MemoryStack = MemoryStack.stackGet()) = createBuffer(
        stack.nmalloc(bytes * capacity),
        (bytes * capacity).toLong()
    )

    /**
     * Create a buffer of instances on the stack.
     */
    fun callocStack(capacity: Int, stack: MemoryStack = MemoryStack.stackGet()) = createBuffer(
        stack.ncalloc(1, capacity, bytes),
        (bytes * capacity).toLong()
    )
}

fun <S : MvStruct<S>> Collection<S>.toBuffer(allocator: (capacity: Int) -> MvStructBuffer<S>) =
    allocator(size).also {
        this.forEachIndexed(it::put)
    }

fun sizeof(structType: MvStructType<*>) = structType.bytes


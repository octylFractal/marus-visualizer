package net.octyl.marus.util.struct

import org.lwjgl.system.MemoryUtil.memAddress
import java.nio.ByteBuffer

data class Layout(
    val members: List<Member<*>>
) {
    constructor(vararg members: Member<*>) : this(listOf(*members))

    val size = members.sumBy { it.type.bytes }

    private val offsets: Map<Member<*>, Int>

    init {
        val offsetsMap = mutableMapOf<Member<*>, Int>()
        var offset = 0
        for (m in members) {
            offsetsMap[m] = offset
            offset += m.type.bytes
        }
        offsets = offsetsMap.toMap()
    }

    fun memberOffset(member: Member<*>): Int =
        offsets[member] ?: error("$member is not part of this layout")

}

fun Member<*>.offsetIn(layout: Layout) = layout.memberOffset(this)

fun <T> ByteBuffer.get(member: Member<T>, layout: Layout): T =
    member.type.read(memAddress(this), layout.memberOffset(member))

fun <T> ByteBuffer.put(member: Member<T>, layout: Layout, value: T) =
    member.type.write(memAddress(this), layout.memberOffset(member), value)


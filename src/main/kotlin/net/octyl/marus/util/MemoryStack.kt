/*
 * Copyright (c) Octavia Togami <https://octyl.net>
 * Copyright (c) contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.octyl.marus.util

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.Struct
import org.lwjgl.system.StructBuffer
import java.nio.IntBuffer

inline fun <C> listAllElements(
    malloc: (Int, MemoryStack) -> C,
    stack: MemoryStack = MemoryStack.stackGet(),
    function: (count: IntBuffer, output: C?) -> Unit
): C {
    val count = stack.mallocInt(1)
    function(count, null)
    val buffer = malloc(count[0], stack)
    function(count, buffer)
    return buffer
}

inline fun <S, B : StructBuffer<S, B>> structs(structBuffer: (capacity: Int) -> B,
                                               vararg structs: S): B {
    val buffer = structBuffer(structs.size)
    for ((index, struct) in structs.withIndex()) {
        buffer.put(index, struct)
    }
    return buffer
}

/**
 * Push and register a new [MemoryStack] with the [CloserScope]. It will be popped
 * with the [CloserScope].
 */
fun CloserScope.pushStack(): MemoryStack = MemoryStack.stackPush().register()

inline fun <S : Struct, B : StructBuffer<S, B>> S.stackPointer(bufferAlloc: (count: Int, stack: MemoryStack) -> B,
                                                               stack: MemoryStack = MemoryStack.stackGet()): B {
    return bufferAlloc(1, stack).put(0, this)
}

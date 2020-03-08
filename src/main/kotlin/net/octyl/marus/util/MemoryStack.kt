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
import org.lwjgl.system.MemoryUtil
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

inline fun <S, B : StructBuffer<S, B>> MemoryStack.structs(structBuffer: (capacity: Int, stack: MemoryStack) -> B,
                                                           vararg structs: S): B {
    val buffer = structBuffer(structs.size, this)
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

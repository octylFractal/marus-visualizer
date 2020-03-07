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

import org.lwjgl.BufferUtils
import org.lwjgl.system.CustomBuffer
import java.nio.Buffer
import java.nio.LongBuffer

/**
 * Perform [block] for each index in the buffer.
 */
inline fun <C : CustomBuffer<C>> C.forEach(block: C.(index: Int) -> Unit) {
    for (i in position() until limit()) {
        block(i)
    }
}

/**
 * Perform [block] for each index in the buffer.
 */
inline fun <B : Buffer> B.forEach(block: B.(index: Int) -> Unit) {
    for (i in position() until limit()) {
        block(i)
    }
}

inline fun <C : CustomBuffer<C>, T> C.asSequence(crossinline block: C.(index: Int) -> T): Sequence<T> {
    return sequence {
        forEach {
            yield(block(it))
        }
    }
}

inline fun <B : Buffer, T> B.asSequence(crossinline block: B.(index: Int) -> T): Sequence<T> {
    return sequence {
        forEach {
            yield(block(it))
        }
    }
}

fun LongBuffer.exportAsDirect(): LongBuffer = BufferUtils.createLongBuffer(remaining())
    .put(this)
    .flip()

fun Buffer.incrementPosition(): Buffer = position(position() + 1)

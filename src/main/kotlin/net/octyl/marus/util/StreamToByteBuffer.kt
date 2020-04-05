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

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels

fun InputStream.toByteBuffer(allocator: Allocator = StdAllocator.JVM_MANAGED): ByteBuffer {
    val channel = Channels.newChannel(this)
    var buffer = allocator.malloc(4096)
    while (channel.isOpen) {
        val read = channel.read(buffer)
        if (read == -1) {
            break
        }
        if (buffer.remaining() == 0) {
            // grow by cap / 2
            val grow = (buffer.capacity() ushr 1)
            buffer = allocator.realloc(buffer, buffer.capacity() + grow)
        }
    }
    return allocator.realloc(buffer, buffer.limit()).flip()
}

inline fun byteBuffer(
    allocator: Allocator = StdAllocator.JVM_MANAGED,
    stream: () -> InputStream
) = stream().use { it.toByteBuffer(allocator) }


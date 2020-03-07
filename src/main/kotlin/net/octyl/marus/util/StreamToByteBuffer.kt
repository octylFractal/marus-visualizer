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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer

fun InputStream.toByteBuffer(): ByteBuffer {
    val cap = ByteArrayOutputStream()
    copyTo(cap)
    val buffer = BufferUtils.createByteBuffer(cap.size())
    buffer.put(cap.toByteArray())
    buffer.flip()
    return buffer
}

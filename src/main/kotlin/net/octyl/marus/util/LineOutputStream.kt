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

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

typealias LineConsumer = (line: String) -> Unit

/**
 * [OutputStream] implementation that forwards to a [LineConsumer].
 */
class LineOutputStream(
    private val lineConsumer: LineConsumer
) : OutputStream() {

    private class ExposedBAOS : ByteArrayOutputStream() {
        val buffer: ByteBuffer
            get() = ByteBuffer.wrap(buf, 0, count)
    }

    private val buffer = ExposedBAOS()

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        var nextStart = 0
        for (i in 0 until len) {
            if (b[i + off] == '\n'.toByte()) {
                buffer.write(b, nextStart + off, i - nextStart)
                cutLine()
                nextStart = i + 1
            }
        }
        buffer.write(b, nextStart + off, len - nextStart)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        if (b == '\n'.toInt()) {
            cutLine()
            return
        }
        buffer.write(b)
    }

    private fun cutLine() {
        val lineBytes = buffer.buffer
        val line = StandardCharsets.UTF_8.decode(lineBytes).toString()
        lineConsumer(line)
        buffer.reset()
    }

    @Throws(IOException::class)
    override fun close() {
        if (buffer.size() > 0) {
            cutLine()
        }
    }
}

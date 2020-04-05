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

package net.octyl.marus.vulkan

import net.octyl.marus.Resources
import net.octyl.marus.util.StdAllocator
import net.octyl.marus.util.byteBuffer
import net.octyl.marus.util.closerWithStack
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.system.MemoryUtil.memUTF8
import org.lwjgl.util.shaderc.Shaderc.*
import org.lwjgl.util.shaderc.ShadercIncludeResolve
import org.lwjgl.util.shaderc.ShadercIncludeResult
import org.lwjgl.util.shaderc.ShadercIncludeResultRelease
import java.io.IOException
import java.nio.ByteBuffer

fun compileGlslToSpirv(classPath: String, stage: ShaderStage): ByteBuffer {
    val src: ByteBuffer = byteBuffer(StdAllocator.APP_MANAGED) { Resources.getResource(classPath) }
    val compiler: Long = shaderc_compiler_initialize()
    val options: Long = shaderc_compile_options_initialize()
    val resolver = ShadercIncludeResolve.create { _, requested_source, _, _, _ ->
        val res: ShadercIncludeResult = ShadercIncludeResult.calloc()
        try {
            val source = classPath.substring(0, classPath.lastIndexOf('/')) + "/" + memUTF8(requested_source)
            res.content(byteBuffer(StdAllocator.APP_MANAGED) { Resources.getResource(source) })
            res.source_name(memUTF8(source))
            res.address()
        } catch (e: IOException) {
            res.free()
            throw AssertionError("Failed to resolve include: $requested_source")
        }
    }
    val releaser = ShadercIncludeResultRelease.create { _, include_result ->
        val result = ShadercIncludeResult.create(include_result)
        memFree(result.content())
        memFree(result.source_name())
        result.free()
    }
    shaderc_compile_options_set_optimization_level(options, shaderc_optimization_level_performance)
    shaderc_compile_options_set_include_callbacks(options, resolver, releaser, 0L)
    val res = closerWithStack { stack ->
        register(src) { memFree(it) }
        shaderc_compile_into_spv(
            compiler, src, stage.shaderc,
            stack.UTF8(classPath), stack.UTF8("main"), options
        )
            .takeUnless { it == NULL }
            ?: error("Internal error during compilation!")
    }
    check(shaderc_result_get_compilation_status(res) == shaderc_compilation_status_success) {
        "Shader compilation failed: " + shaderc_result_get_error_message(res)
    }
    val size = shaderc_result_get_length(res).toInt()
    val resultBytes = BufferUtils.createByteBuffer(size)
    resultBytes.put(shaderc_result_get_bytes(res))
    resultBytes.flip()
    shaderc_compiler_release(res)
    shaderc_compiler_release(compiler)
    releaser.free()
    resolver.free()
    return resultBytes
}

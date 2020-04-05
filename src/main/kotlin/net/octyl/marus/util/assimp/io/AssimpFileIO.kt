package net.octyl.marus.util.assimp.io

import mu.KotlinLogging
import org.lwjgl.assimp.AIFile
import org.lwjgl.assimp.AIFileIO
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.memAddress
import org.lwjgl.system.MemoryUtil.memByteBuffer
import org.lwjgl.system.MemoryUtil.memCopy
import org.lwjgl.system.MemoryUtil.memFree
import org.lwjgl.system.MemoryUtil.memUTF8
import java.nio.ByteBuffer
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption.READ
import java.util.concurrent.ConcurrentHashMap

private val LOGGER = KotlinLogging.logger { }

/**
 * Convert a file load callback to an AssImp [AIFileIO].
 *
 * The corresponding [AIFileIO] must be explicitly freed, as well as the individual
 * [AIFileIO.OpenProc] and [AIFileIO.CloseProc] callbacks. [AIFileIO.recursiveFree]
 * is an easy option.
 *
 * @param loadFile the callback for loading a file by name, must return a direct buffer, will be explicitly freed
 * by this object
 */
fun assImpFileIO(loadFile: (name: String) -> ByteBuffer): AIFileIO {
    return AIFileIO.calloc()
        .OpenProc { _, fileName, openMode ->
            // validate open-as-read only
            openOptions(memUTF8(openMode))
            val name = memUTF8(fileName)
            loadFile(name).toAssimpFile(name).address()
        }
        .CloseProc { _, pFile ->
            when (val buffer = OPEN_BUFFERS.remove(pFile)) {
                null -> {
                    LOGGER.warn {
                        val filePointer = pFile.toString(radix = 16).padStart(16, padChar = '0')
                        "Closing file that's already closed: 0x$filePointer"
                    }
                }
                else -> {
                    memFree(buffer)
                    AIFile.create(pFile).recursiveFree()
                }
            }
        }
}

fun AIFileIO.recursiveFree() {
    OpenProc().free()
    CloseProc().free()
    free()
}

fun AIFile.recursiveFree() {
    FileSizeProc().free()
    FlushProc().free()
    ReadProc().free()
    SeekProc().free()
    TellProc().free()
    WriteProc().free()
    free()
}

fun openOptions(openMode: String): Set<OpenOption> {
    return when (openMode[0]) {
        'w' -> error("Cannot write to files. Please stop.")
        'r' -> setOf(READ)
        else -> error("Unknown open mode: $openMode")
    }
}

/**
 * Record of channels open as [AIFile]s by address. This allows us to cleanly close them
 * when the associated [AIFile] is closed.
 *
 * This does rely on AssImp being good about closing files, but it should be.
 */
private val OPEN_BUFFERS = ConcurrentHashMap<Long, ByteBuffer>()

fun ByteBuffer.toAssimpFile(nameHint: String? = null): AIFile {
    val nameText = nameHint?.let { " (name: $nameHint)" }
    val original = this
    val fileSize = original.remaining().toLong()
    val baseAddress = memAddress(original)
    var pointer = 0L

    return AIFile.calloc()
        .FileSizeProc { fileSize }
        .FlushProc { }
        .ReadProc { _, pBuffer, size, count ->
            // min count is (remaining / size); will round down
            val realCount = ((fileSize - pointer) / size).coerceAtMost(count)
            val amount = size * realCount
            if (amount <= 0) {
                // EOF
                return@ReadProc 0
            }
            memCopy(baseAddress + pointer, pBuffer, amount)
            pointer += amount
            realCount
        }
        .SeekProc { _, offset, origin ->
            try {
                pointer = when (origin) {
                    0 -> // SEEK_SET
                        offset
                    1 -> // SEEK_CUR
                        pointer + offset
                    2 -> // SEEK_END
                        fileSize + offset
                    else -> error("Unknown seek origin: $origin")
                }
                0
            } catch (e: Exception) {
                LOGGER.error(e) { "Error seeking in channel$nameText" }
                1
            }
        }
        .TellProc { pointer }
        .WriteProc { _, _, _, _ ->
            LOGGER.error(Exception("stacktrace provider")) { "Writing not allowed$nameText" }
            -1
        }
        .also {
            OPEN_BUFFERS[it.address()] = original
        }
}

package net.octyl.marus.util

import org.lwjgl.vulkan.VK10
import java.lang.reflect.Modifier

object VkFormatName {
    private val name = VK10::class.java.fields.asSequence()
        .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("VK_FORMAT_") }
        .associate { it.get(null) to it.name }

    operator fun get(format: Int) =
        name[format] ?: "Unknown ($format)"
}

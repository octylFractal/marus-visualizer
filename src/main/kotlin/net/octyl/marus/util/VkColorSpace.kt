package net.octyl.marus.util

import org.lwjgl.vulkan.EXTSwapchainColorspace
import org.lwjgl.vulkan.KHRSurface
import java.lang.reflect.Modifier

object VkColorSpace {
    private val name = (KHRSurface::class.java.fields.asSequence()
        + EXTSwapchainColorspace::class.java.fields.asSequence())
        .filter { Modifier.isStatic(it.modifiers) && it.name.startsWith("VK_COLOR_SPACE_") }
        .associate { it.get(null) to it.name }

    operator fun get(format: Int) =
        name[format] ?: "Unknown ($format)"
}

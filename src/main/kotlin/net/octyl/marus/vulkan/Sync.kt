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

import net.octyl.marus.MAX_FRAMES_IN_FLIGHT
import net.octyl.marus.util.closer
import net.octyl.marus.util.exportAsDirect
import net.octyl.marus.util.incrementPosition
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageAvailableSemaphores
import net.octyl.marus.vkImagesInFlight
import net.octyl.marus.vkInflightFences
import net.octyl.marus.vkRenderFinishedSemaphores
import net.octyl.marus.vkSwapChainImages
import org.lwjgl.BufferUtils
import org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateFence
import org.lwjgl.vulkan.VK10.vkCreateSemaphore
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.lwjgl.vulkan.VkSemaphoreCreateInfo

fun createSyncObjects() {
    closer {
        val stack = pushStack()
        val semaphoreInfo = VkSemaphoreCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO)
        val fenceInfo = VkFenceCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
            .flags(VK_FENCE_CREATE_SIGNALED_BIT)
        val imageAvailableSemaphores = stack.mallocLong(MAX_FRAMES_IN_FLIGHT)
        val renderFinishedSemaphores = stack.mallocLong(MAX_FRAMES_IN_FLIGHT)
        val inflightFences = stack.callocLong(MAX_FRAMES_IN_FLIGHT)
        for (i in 0 until MAX_FRAMES_IN_FLIGHT) {
            checkedCreate({ "image available semaphore $i" }) {
                vkCreateSemaphore(vkDevice, semaphoreInfo, null, imageAvailableSemaphores)
            }
            imageAvailableSemaphores.incrementPosition()
            checkedCreate({ "render finished semaphore $i" }) {
                vkCreateSemaphore(vkDevice, semaphoreInfo, null, renderFinishedSemaphores)
            }
            renderFinishedSemaphores.incrementPosition()
            checkedCreate({ "in-flight fence $i" }) {
                vkCreateFence(vkDevice, fenceInfo, null, inflightFences)
            }
            inflightFences.incrementPosition()
        }
        imageAvailableSemaphores.flip()
        renderFinishedSemaphores.flip()
        inflightFences.flip()
        vkImageAvailableSemaphores = imageAvailableSemaphores.exportAsDirect()
        vkRenderFinishedSemaphores = renderFinishedSemaphores.exportAsDirect()
        vkInflightFences = inflightFences.exportAsDirect()
        vkImagesInFlight = BufferUtils.createLongBuffer(vkSwapChainImages.size)
    }
}

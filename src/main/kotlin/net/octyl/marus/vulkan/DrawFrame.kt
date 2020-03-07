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
import net.octyl.marus.swapChainOutdated
import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkCommandBuffers
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageAvailableSemaphores
import net.octyl.marus.vkImagesInFlight
import net.octyl.marus.vkInflightFences
import net.octyl.marus.vkRenderFinishedSemaphores
import net.octyl.marus.vkSwapChain
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR
import org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR
import org.lwjgl.vulkan.VK10.VK_NULL_HANDLE
import org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO
import org.lwjgl.vulkan.VK10.vkQueueSubmit
import org.lwjgl.vulkan.VK10.vkResetFences
import org.lwjgl.vulkan.VK10.vkWaitForFences
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkSubmitInfo

var currentFrame = 0

private val RESIZE_RESULTS = setOf(VK_ERROR_OUT_OF_DATE_KHR, VK_SUBOPTIMAL_KHR)
private val RESIZE_SUCCESS = VK_STD_SUCCESS_CODES + RESIZE_RESULTS

fun drawFrame() {
    closer {
        val stack = pushStack()
        val imageAvailableSemaphore = vkImageAvailableSemaphores[currentFrame]
        val renderFinishedSemaphore = vkRenderFinishedSemaphores[currentFrame]
        val inflightFence = vkInflightFences[currentFrame]
        vkWaitForFences(vkDevice, inflightFence, true, Long.MAX_VALUE)

        val imageIndex = stack.mallocInt(1)
        val result = checkedAction("acquire next image", successCodes = RESIZE_SUCCESS) {
            vkAcquireNextImageKHR(
                vkDevice, vkSwapChain, Long.MAX_VALUE, imageAvailableSemaphore, VK_NULL_HANDLE, imageIndex
            )
        }
        if (result == VK_ERROR_OUT_OF_DATE_KHR) {
            recreateSwapChain()
            return@closer
        }

        val oldImageFence = vkImagesInFlight[imageIndex[0]]
        if (oldImageFence != VK_NULL_HANDLE) {
            vkWaitForFences(vkDevice, stack.longs(oldImageFence), true, Long.MAX_VALUE)
        }
        vkImagesInFlight.put(imageIndex[0], inflightFence)

        val submitInfo = VkSubmitInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
        val waitSemaphores = stack.longs(imageAvailableSemaphore)
        val waitStages = stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        submitInfo.waitSemaphoreCount(waitSemaphores.remaining())
            .pWaitSemaphores(waitSemaphores)
            .pWaitDstStageMask(waitStages)
            .pCommandBuffers(stack.pointers(vkCommandBuffers[imageIndex[0]]))
        val signalSemaphores = stack.longs(renderFinishedSemaphore)
        submitInfo.pSignalSemaphores(signalSemaphores)

        vkResetFences(vkDevice, inflightFence)
        checkedAction("queue submit") {
            vkQueueSubmit(queues.graphicsQueue!!.queueHandle!!, submitInfo, inflightFence)
        }

        val presentInfo = VkPresentInfoKHR.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(signalSemaphores)
        val swapChains = stack.longs(vkSwapChain)
        presentInfo.swapchainCount(swapChains.remaining())
            .pSwapchains(swapChains)
            .pImageIndices(imageIndex)
        val queuePresentResult = checkedAction("queue present", successCodes = RESIZE_SUCCESS) {
            vkQueuePresentKHR(queues.presentQueue!!.queueHandle!!, presentInfo)
        }
        if (swapChainOutdated || queuePresentResult in RESIZE_RESULTS) {
            swapChainOutdated = false
            recreateSwapChain()
        }
        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT
    }
}

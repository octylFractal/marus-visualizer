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
import net.octyl.marus.data.UniformBufferObject
import net.octyl.marus.data.copyTo
import net.octyl.marus.swapChainOutdated
import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.struct.memByteBuffer
import net.octyl.marus.vkCommandBuffers
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageAvailableSemaphores
import net.octyl.marus.vkImagesInFlight
import net.octyl.marus.vkInflightFences
import net.octyl.marus.vkRenderFinishedSemaphores
import net.octyl.marus.vkSwapChain
import net.octyl.marus.vkUniformBuffers
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.system.MemoryUtil.memAddress
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
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

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
        val result = checkedAction({ "acquire next image" }, successCodes = RESIZE_SUCCESS) {
            vkAcquireNextImageKHR(
                vkDevice, vkSwapChain, Long.MAX_VALUE, imageAvailableSemaphore, VK_NULL_HANDLE, imageIndex
            )
        }
        if (result == VK_ERROR_OUT_OF_DATE_KHR) {
            recreateSwapChain()
            return@closer
        }

        val currentImage = imageIndex[0]
        val oldImageFence = vkImagesInFlight[currentImage]
        if (oldImageFence != VK_NULL_HANDLE) {
            vkWaitForFences(vkDevice, stack.longs(oldImageFence), true, Long.MAX_VALUE)
        }
        vkImagesInFlight.put(currentImage, inflightFence)

        updateUniformBuffer(currentImage)

        val submitInfo = VkSubmitInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
        val waitSemaphores = stack.longs(imageAvailableSemaphore)
        val waitStages = stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
        submitInfo.waitSemaphoreCount(waitSemaphores.remaining())
            .pWaitSemaphores(waitSemaphores)
            .pWaitDstStageMask(waitStages)
            .pCommandBuffers(stack.pointers(vkCommandBuffers[currentImage]))
        val signalSemaphores = stack.longs(renderFinishedSemaphore)
        submitInfo.pSignalSemaphores(signalSemaphores)

        vkResetFences(vkDevice, inflightFence)
        checkedAction({ "queue submit" }) {
            vkQueueSubmit(queues.graphicsQueue!!.queueHandle!!, submitInfo, inflightFence)
        }

        val presentInfo = VkPresentInfoKHR.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
            .pWaitSemaphores(signalSemaphores)
        val swapChains = stack.longs(vkSwapChain)
        presentInfo.swapchainCount(swapChains.remaining())
            .pSwapchains(swapChains)
            .pImageIndices(imageIndex)
        val queuePresentResult = checkedAction({ "queue present" }, successCodes = RESIZE_SUCCESS) {
            vkQueuePresentKHR(queues.presentQueue!!.queueHandle!!, presentInfo)
        }
        if (swapChainOutdated || queuePresentResult in RESIZE_RESULTS) {
            swapChainOutdated = false
            recreateSwapChain()
        }
        currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT
    }
}

@OptIn(ExperimentalTime::class)
private val timer = TimeSource.Monotonic.markNow()
private val UBO = UniformBufferObject.create()
@OptIn(ExperimentalTime::class)
private var lastTime = timer.elapsedNow().inSeconds.toFloat()
private var counter = 0

@OptIn(ExperimentalTime::class)
fun updateUniformBuffer(image: Int) {
    val time = timer.elapsedNow().inSeconds.toFloat()
    counter++
    if (time - lastTime > 1.0) {
        println("FPS: ${counter / (time - lastTime)}")
        lastTime = time
        counter = 0
    }
    Matrix4f().rotate(time * Math.toRadians(90.0).toFloat() / 2, Vector3f(0.0f, 0.0f, 1.0f))
        .copyTo(UBO.model())
    Matrix4f().lookAt(2.0f, 2.0f, 2.0f,
            0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f)
        .copyTo(UBO.view())
    Matrix4f().perspective(Math.toRadians(45.0).toFloat(),
            vkSwapChainExtent.width().toFloat() / vkSwapChainExtent.height(),
            0.1f, 10.0f)
        .apply {
            set(1, 1, get(1, 1) * -1)
        }
        .copyTo(UBO.proj())
    vkUniformBuffers[image].copyFrom(vkDevice, memAddress(memByteBuffer(UBO)))
}

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

import net.octyl.marus.INDICIES
import net.octyl.marus.util.closer
import net.octyl.marus.util.forEach
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkCommandBuffers
import net.octyl.marus.vkCommandPool
import net.octyl.marus.vkDescriptorSets
import net.octyl.marus.vkDevice
import net.octyl.marus.vkIndexBuffer
import net.octyl.marus.vkPipeline
import net.octyl.marus.vkPipelineLayout
import net.octyl.marus.vkRenderPass
import net.octyl.marus.vkSwapChainFramebuffers
import net.octyl.marus.vkVertexBuffer
import org.lwjgl.BufferUtils
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkRenderPassBeginInfo

fun createCommandBuffers() {
    closer {
        val stack = pushStack()

        val commandBuffers = stack.mallocPointer(vkSwapChainFramebuffers.size)
        val allocInfo = VkCommandBufferAllocateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
            .commandPool(vkCommandPool)
            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
            .commandBufferCount(commandBuffers.remaining())
        checkedCreate("command buffers") {
            vkAllocateCommandBuffers(vkDevice, allocInfo, commandBuffers)
        }

        val beginInfo = VkCommandBufferBeginInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
        val clearColor = VkClearValue.callocStack(stack).color {
            it.float32().put(floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f))
        }
        val renderPassInfo = VkRenderPassBeginInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
            .renderPass(vkRenderPass)
            .renderArea {
                it.offset { offset -> offset.set(0, 0) }
                it.extent(vkSwapChainExtent)
            }
            .pClearValues(stack.structs(VkClearValue::mallocStack, clearColor))
        commandBuffers.forEach {
            val commandBuffer = VkCommandBuffer(get(it), vkDevice)
            checkedAction("begin command buffer $it") {
                vkBeginCommandBuffer(commandBuffer, beginInfo)
            }
            renderPassInfo.framebuffer(vkSwapChainFramebuffers[it])
            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, vkPipeline)

            vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(vkVertexBuffer), stack.longs(0))
            vkCmdBindIndexBuffer(commandBuffer, vkIndexBuffer, 0, VK_INDEX_TYPE_UINT32)

            vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, vkPipelineLayout,
                0, stack.longs(vkDescriptorSets[it]), null)

            vkCmdDrawIndexed(commandBuffer, INDICIES.remaining(), 1, 0, 0, 0)

            vkCmdEndRenderPass(commandBuffer)
            checkedAction("end command buffer $it") {
                vkEndCommandBuffer(commandBuffer)
            }
        }

        vkCommandBuffers = BufferUtils.createPointerBuffer(commandBuffers.remaining())
            .put(commandBuffers)
            .flip()
    }
}

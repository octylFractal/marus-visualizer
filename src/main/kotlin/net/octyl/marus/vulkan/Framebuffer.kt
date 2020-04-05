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

import net.octyl.marus.util.closer
import net.octyl.marus.util.forEach
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkColorImageView
import net.octyl.marus.vkDepthImageView
import net.octyl.marus.vkDevice
import net.octyl.marus.vkImageViews
import net.octyl.marus.vkRenderPass
import net.octyl.marus.vkSwapChainFramebuffers
import org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO
import org.lwjgl.vulkan.VK10.vkCreateFramebuffer
import org.lwjgl.vulkan.VkFramebufferCreateInfo

fun createFramebuffer() {
    closer {
        val stack = pushStack()
        val framebuffers = stack.mallocLong(vkImageViews.size)
        framebuffers.forEach {
            val attachments = stack.longs(vkColorImageView, vkDepthImageView, vkImageViews[it])

            val framebufferInfo = VkFramebufferCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .renderPass(vkRenderPass)
                .pAttachments(attachments)
                .width(vkSwapChainExtent.width())
                .height(vkSwapChainExtent.height())
                .layers(1)

            checkedCreate({ "framebuffer $it" }) {
                vkCreateFramebuffer(vkDevice, framebufferInfo, null, framebuffers)
            }
            framebuffers.position(framebuffers.position() + 1)
        }
        framebuffers.flip()
        vkSwapChainFramebuffers = LongArray(framebuffers.remaining()).also {
            framebuffers.get(it)
        }
    }
}

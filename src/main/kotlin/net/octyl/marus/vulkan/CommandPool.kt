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
import net.octyl.marus.util.pushStack
import net.octyl.marus.vkCommandPool
import net.octyl.marus.vkDevice
import org.lwjgl.vulkan.VK10
import org.lwjgl.vulkan.VK10.vkCreateCommandPool
import org.lwjgl.vulkan.VkCommandPoolCreateInfo

fun createCommandPool() {
    closer {
        val stack = pushStack()
        val poolInfo = VkCommandPoolCreateInfo.callocStack(stack)
            .sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
            .queueFamilyIndex(vkPhysicalDevice.findQueues().graphicsQueue!!.index)

        val commandPool = stack.mallocLong(1)
        checkedCreate({ "command pool" }) {
            vkCreateCommandPool(vkDevice, poolInfo, null, commandPool)
        }
        vkCommandPool = commandPool[0]
    }
}

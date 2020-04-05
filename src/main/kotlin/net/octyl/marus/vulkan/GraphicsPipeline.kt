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

import net.octyl.marus.data.Vertex
import net.octyl.marus.util.closer
import net.octyl.marus.util.pushStack
import net.octyl.marus.util.structs
import net.octyl.marus.vkDescriptorSetLayout
import net.octyl.marus.vkDevice
import net.octyl.marus.vkPipeline
import net.octyl.marus.vkPipelineLayout
import net.octyl.marus.vkRenderPass
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkOffset2D
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSubpassDependency
import org.lwjgl.vulkan.VkSubpassDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import org.lwjgl.vulkan.VkViewport


fun createRenderPass() {
    closer {
        val stack = pushStack()
        val colorAttachment = VkAttachmentDescription.callocStack(stack)
            .format(vkSwapChainFormat)
            .samples(msaaSamples)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        var attachments = 0
        val colorAttachmentRef = VkAttachmentReference.callocStack(stack)
            .attachment(attachments++)
            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        val depthAttachment = VkAttachmentDescription.callocStack(stack)
            .format(vkDepthFormat)
            .samples(msaaSamples)
            .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
            .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        val depthAttachmentRef = VkAttachmentReference.callocStack(stack)
            .attachment(attachments++)
            .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
        val colorAttachmentResolve = VkAttachmentDescription.callocStack(stack)
            .format(vkSwapChainFormat)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
            .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
        val colorAttachmentResolveRef = VkAttachmentReference.callocStack(stack)
            .attachment(attachments)
            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
        val subpass = VkSubpassDescription.callocStack(stack)
            .colorAttachmentCount(1)
            .pColorAttachments(stack.structs(VkAttachmentReference::mallocStack, colorAttachmentRef))
            .pResolveAttachments(stack.structs(VkAttachmentReference::mallocStack, colorAttachmentResolveRef))
            .pDepthStencilAttachment(depthAttachmentRef)
        val dependency = VkSubpassDependency.callocStack(stack)
            .srcSubpass(VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .srcAccessMask(0)
            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT or VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
        val renderPassInfo = VkRenderPassCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
            .pAttachments(stack.structs(VkAttachmentDescription::mallocStack,
                colorAttachment, depthAttachment, colorAttachmentResolve
            ))
            .pSubpasses(stack.structs(VkSubpassDescription::mallocStack, subpass))
            .pDependencies(stack.structs(VkSubpassDependency::mallocStack, dependency))

        val renderPass = stack.mallocLong(1)
        checkedCreate({ "render pass" }) {
            vkCreateRenderPass(vkDevice, renderPassInfo, null, renderPass)
        }
        vkRenderPass = renderPass[0]
    }
}

fun createGraphicsPipeline() {
    closer {
        val vertShader = Shader.compileAndLoadShader("vertex.vert", ShaderStage.VERTEX)
        val fragShader = Shader.compileAndLoadShader("fragment.frag", ShaderStage.FRAGMENT)
        val vertShaderModule = vertShader
            .createVkShaderModule()
            .register { vkDestroyShaderModule(vkDevice, it, null) }
        val fragShaderModule = fragShader
            .createVkShaderModule()
            .register { vkDestroyShaderModule(vkDevice, it, null) }

        val stack = pushStack()
        val stageInfos = VkPipelineShaderStageCreateInfo.mallocStack(2, stack)
            .put(vertShader.setupStageInfo(vertShaderModule))
            .put(fragShader.setupStageInfo(fragShaderModule))
            .flip()
        val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
            .pVertexBindingDescriptions(stack.structs(VkVertexInputBindingDescription::mallocStack,
                Vertex.bindingDescription
            ))
            .pVertexAttributeDescriptions(Vertex.attributeDescription)
        val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
            .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
            .primitiveRestartEnable(false)
        val viewport = VkViewport.callocStack(stack)
            .x(0.0f)
            .y(0.0f)
            .width(vkSwapChainExtent.width().toFloat())
            .height(vkSwapChainExtent.height().toFloat())
            .minDepth(0.0f)
            .maxDepth(1.0f)
        val scissor = VkRect2D.callocStack(stack)
            .offset(VkOffset2D.callocStack(stack).set(0, 0))
            .extent(vkSwapChainExtent)
        val viewportState = VkPipelineViewportStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
            .pViewports(stack.structs(VkViewport::mallocStack, viewport))
            .pScissors(stack.structs(VkRect2D::mallocStack, scissor))
        val rasterizer = VkPipelineRasterizationStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
            .depthClampEnable(false)
            .rasterizerDiscardEnable(false)
            .polygonMode(VK_POLYGON_MODE_FILL)
            .lineWidth(1.0f)
            .cullMode(VK_CULL_MODE_BACK_BIT)
            .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
            .depthBiasEnable(false)
        val multiSampling = VkPipelineMultisampleStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
            .sampleShadingEnable(false)
            .rasterizationSamples(msaaSamples)
        val colorBlendAttachment = VkPipelineColorBlendAttachmentState.callocStack(stack)
            .colorWriteMask(VK_COLOR_COMPONENT_R_BIT or
                VK_COLOR_COMPONENT_G_BIT or
                VK_COLOR_COMPONENT_B_BIT or
                VK_COLOR_COMPONENT_A_BIT)
            .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
            .dstColorBlendFactor(VK_BLEND_FACTOR_ZERO)
            .colorBlendOp(VK_BLEND_OP_ADD)
            .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
            .dstAlphaBlendFactor(VK_BLEND_FACTOR_ZERO)
            .alphaBlendOp(VK_BLEND_OP_ADD)
        val colorBlending = VkPipelineColorBlendStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
            .logicOpEnable(false)
            .pAttachments(stack.structs(VkPipelineColorBlendAttachmentState::mallocStack, colorBlendAttachment))
        val depthStencil = VkPipelineDepthStencilStateCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
            .depthTestEnable(true)
            .depthWriteEnable(true)
            .depthCompareOp(VK_COMPARE_OP_LESS)

        createPipelineLayout()

        val pipelineInfo = VkGraphicsPipelineCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
            .pStages(stageInfos)
            .pVertexInputState(vertexInputInfo)
            .pInputAssemblyState(inputAssembly)
            .pViewportState(viewportState)
            .pRasterizationState(rasterizer)
            .pMultisampleState(multiSampling)
            .pColorBlendState(colorBlending)
            .pDepthStencilState(depthStencil)
            .layout(vkPipelineLayout)
            .renderPass(vkRenderPass)
            .subpass(0)
            .basePipelineHandle(VK_NULL_HANDLE)
            .basePipelineIndex(-1)

        val pipeline = stack.mallocLong(1)
        checkedCreate({ "pipeline" }) {
            vkCreateGraphicsPipelines(vkDevice, VK_NULL_HANDLE,
                stack.structs(VkGraphicsPipelineCreateInfo::mallocStack, pipelineInfo),
                null, pipeline)
        }
        vkPipeline = pipeline[0]
    }
}

private fun createPipelineLayout() {
    closer {
        val stack = pushStack()
        val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.callocStack(stack)
            .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
            .pSetLayouts(stack.longs(vkDescriptorSetLayout))
        val pipelineLayout = stack.mallocLong(1)
        checkedCreate({ "pipeline layout" }) {
            vkCreatePipelineLayout(vkDevice, pipelineLayoutInfo, null, pipelineLayout)
        }
        vkPipelineLayout = pipelineLayout.get()
    }
}

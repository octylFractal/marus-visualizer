package net.octyl.marus.data.obj

import mu.KotlinLogging
import net.octyl.marus.Resources
import net.octyl.marus.data.Vertex
import net.octyl.marus.util.Allocator
import net.octyl.marus.util.asSequence
import net.octyl.marus.util.struct.callocStruct
import org.lwjgl.assimp.AIFileIO
import org.lwjgl.assimp.AILogStream
import org.lwjgl.assimp.AILogStreamCallback
import org.lwjgl.assimp.AIMaterial
import org.lwjgl.assimp.AIMaterialProperty
import org.lwjgl.assimp.AIMesh
import org.lwjgl.assimp.AINode
import org.lwjgl.assimp.AIString
import org.lwjgl.assimp.Assimp.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil

private val AI_LOGGER = KotlinLogging.logger("AssImp")

class Importer(
    private val aiFileIO: AIFileIO,
    private val allocator: Allocator
) {

    fun import(name: String): Scene {
        val aiScene = MemoryStack.stackPush().use { stack ->
            val cb = AILogStreamCallback.create { message, _ ->
                AI_LOGGER.info { MemoryUtil.memUTF8(message).removeSuffix("\n") }
            }
            val logStream = AILogStream.callocStack(stack)
                .callback(cb)
                // needs to be non-null for some reason...
                .user(MemoryUtil.memAddress(stack.bytes(0)))
            aiAttachLogStream(logStream)
            try {
                aiImportFileEx(name, aiProcessPreset_TargetRealtime_Fast or aiProcess_FlipUVs, aiFileIO)
                    ?: error("Error loading '$name': ${aiGetErrorString()}")
            } finally {
                aiDetachLogStream(logStream)
                cb.free()
            }
        }
        check(aiScene.mFlags() and AI_SCENE_FLAGS_INCOMPLETE == 0) {
            "Scene '$name' is incomplete"
        }
        val materials = aiScene.mMaterials()
            ?.asSequence { AIMaterial.create(get(it)) }
            ?.map { import(it) }
            ?.toList()
            .orEmpty()
        val meshes = aiScene.mMeshes()
            ?.asSequence { AIMesh.create(get(it)) }
            ?.map { import(materials, it) }
            ?.toList()
            .orEmpty()
        return Scene(
            import(meshes, aiScene.mRootNode()!!)
        )
    }

    private fun import(material: AIMaterial): Material {
        val textureCount = aiGetMaterialTextureCount(material, aiTextureType_DIFFUSE)
        check(textureCount <= 1) { "Need one diffuse texture, got $textureCount" }
        val texture = when (textureCount) {
            1 -> {
                val path = MemoryStack.stackPush().use { stack ->
                    val aiPath = AIString.callocStack(stack)
                    aiGetMaterialString(material, _AI_MATKEY_TEXTURE_BASE, aiTextureType_DIFFUSE, 0, aiPath)
                    aiPath.dataString()
                }
                Resources.getImage(path)
            }
            else -> null
        }
        return Material(texture)
    }

    private fun import(materials: List<Material>, mesh: AIMesh): Mesh {
        val texCoords = mesh.mTextureCoords(0)
        val vertices = mesh.mVertices().mapIndexed { index, vec ->
            allocator.callocStruct(Vertex)
                .position(vec.x(), vec.y(), vec.z())
                .apply {
                    if (texCoords != null) {
                        val coord = texCoords[index]
                        texture(coord.x(), coord.y())
                    }
                }
        }
        val faces = mesh.mFaces().map { face ->
            Face(face.mIndices().asSequence { get(it) }.toList())
        }
        val material = materials[mesh.mMaterialIndex()]
        return Mesh(
            vertices,
            faces,
            material
        )
    }

    private fun import(meshes: List<Mesh>, node: AINode): Node {
        val children = node.mChildren()
            ?.asSequence { AINode.create(get(it)) }
            ?.map { import(meshes, it) }
            ?.toList()
            .orEmpty()
        return Node(
            children,
            node.mMeshes()
                ?.asSequence { get(it) }
                ?.map { meshes[it] }
                ?.toList()
                .orEmpty()
        )
    }
}

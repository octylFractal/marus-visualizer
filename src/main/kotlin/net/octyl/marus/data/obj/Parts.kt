package net.octyl.marus.data.obj

import net.octyl.marus.data.Vertex
import net.octyl.marus.vulkan.ImageHandles

class Scene(
    val root: Node
)

class Node(
    val children: List<Node>,
    val meshes: List<Mesh>
) {
    fun nodesWithMeshes(): Sequence<Node> = sequence {
        if (meshes.isNotEmpty()) {
            yield(this@Node)
        }
        yieldAll(children.asSequence().flatMap { it.nodesWithMeshes() })
    }

    fun printTree(level: Int = 0) {
        val levelString = "  ".repeat(level) + "-"
        println("$levelString Node(#children=${children.size},#meshes=${meshes.size})")
        for (child in children) {
            child.printTree(level = level + 1)
        }
    }
}

class Mesh(
    val vertices: List<Vertex>,
    val faces: List<Face>,
    val materialIndex: Material
)

class Face(
    val indicies: List<Int>
)

class Material(
    val diffuseTexture: ImageHandles?
)

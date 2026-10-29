package com.xcluice.jetstrike.engine

/** Ready-made low-poly models built from primitives. Swap for real art later; the rigs and names stay the same. */
object Prefabs {
    class Soldier(
        val root: Node,
        /** Hip-pivoted legs: rotate rz to swing them. */
        val legL: Node, val legR: Node,
        /** Shoulder-pivoted gun arm pointing along +x: rotate rz to aim. */
        val arm: Node,
        /** Jetpack flame, toggle visible. */
        val flame: Node,
        /** Children that take the body colour (for hit flashes via emissive). */
        val parts: List<Node>
    ) {
        /** Face right (+x) or left (-x). */
        fun face(right: Boolean) { root.ry = if (right) 0f else Math.PI.toFloat() }
        fun flash(k: Float) { for (p in parts) p.emissive = k }
    }

    private fun part(parent: Node, name: String, mesh: Mesh, x: Float, y: Float, z: Float = 0f): Node {
        val n = Node(mesh)
        n.name = name
        n.setPos(x, y, z)
        parent.add(n)
        return n
    }

    /** About 1.15 units tall with the feet at y = 0, matching Terrain3D's scale. */
    fun soldier(main: Int, dark: Int, light: Int): Soldier {
        val skin = 0xFFE8B98F.toInt()
        val root = Node()
        root.name = "soldier"
        val parts = ArrayList<Node>()

        val legMesh = MeshBuilder().color(dark).box(0f, -0.28f, 0f, 0.17f, 0.56f, 0.17f).build()
        val legL = part(root, "legL", legMesh, 0f, 0.56f, -0.1f)
        val legR = part(root, "legR", legMesh, 0f, 0.56f, 0.1f)
        parts.add(legL); parts.add(legR)

        parts.add(part(root, "torso", MeshBuilder().color(main).box(0f, 0f, 0f, 0.4f, 0.5f, 0.3f).build(), 0f, 0.82f))
        parts.add(part(root, "head", MeshBuilder().color(skin).sphere(0f, 0f, 0f, 0.17f, 12, 8).build(), 0f, 1.2f))
        part(root, "visor", MeshBuilder().color(light).box(0.1f, 0f, 0f, 0.14f, 0.09f, 0.22f).build(), 0.06f, 1.22f)
        part(root, "helmet", MeshBuilder().color(dark).sphere(0f, 0f, 0f, 0.19f, 10, 5).build(), 0f, 1.25f).sy = 0.7f

        val jet = part(root, "jetpack", MeshBuilder().color(dark).box(0f, 0f, 0f, 0.18f, 0.42f, 0.2f).build(), -0.27f, 0.86f)
        parts.add(jet)
        val flameMesh = MeshBuilder().color(0xFFFFB347.toInt()).cylinder(0f, -0.35f, 0f, 0.02f, 0.09f, 0.35f, 8, false, false).build()
        val flame = part(root, "flame", flameMesh, -0.27f, 0.62f)
        flame.emissive = 1f
        flame.visible = false

        val arm = part(root, "arm", MeshBuilder().color(main).box(0.1f, 0f, 0f, 0.28f, 0.11f, 0.11f).build(), 0.04f, 0.95f, 0.18f)
        part(arm, "gun", MeshBuilder().color(0xFF2B2E33.toInt()).box(0.42f, 0.02f, 0f, 0.5f, 0.1f, 0.1f).build(), 0f, 0f)
        parts.add(arm)

        return Soldier(root, legL, legR, arm, flame, parts)
    }

    fun crate(color: Int, size: Float = 0.6f): Node =
        Node(MeshBuilder().color(color).box(0f, size / 2, 0f, size, size, size).build())

    /** Glowing bullet streak along +x. */
    fun tracer(color: Int): Node {
        val n = Node(MeshBuilder().color(color).box(0f, 0f, 0f, 0.5f, 0.05f, 0.05f).build())
        n.emissive = 1f
        return n
    }
}

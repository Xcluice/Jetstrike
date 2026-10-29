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

    /** Chunky cartoon soldier: big head and helmet, round body, stubby limbs. About 1.3 units tall, feet at y = 0. */
    fun soldier(main: Int, dark: Int, light: Int): Soldier {
        val skin = 0xFFE8B98F.toInt()
        val root = Node()
        root.name = "soldier"
        val parts = ArrayList<Node>()

        val legMesh = MeshBuilder().color(dark).box(0f, -0.2f, 0f, 0.2f, 0.4f, 0.2f).sphere(0.03f, -0.4f, 0f, 0.13f, 8, 6).build()
        val legL = part(root, "legL", legMesh, 0f, 0.42f, -0.12f)
        val legR = part(root, "legR", legMesh, 0f, 0.42f, 0.12f)
        parts.add(legL); parts.add(legR)

        val torso = part(root, "torso", MeshBuilder().color(main).sphere(0f, 0f, 0f, 0.32f, 12, 8).build(), 0f, 0.7f)
        torso.sy = 1.05f; torso.sx = 1f
        parts.add(torso)
        part(root, "belt", MeshBuilder().color(dark).box(0f, 0f, 0f, 0.5f, 0.07f, 0.4f).build(), 0f, 0.55f)
        part(root, "strap", MeshBuilder().color(dark).box(0.02f, 0f, 0f, 0.09f, 0.5f, 0.52f).build(), 0.02f, 0.74f).rz = -0.5f

        parts.add(part(root, "head", MeshBuilder().color(skin).sphere(0f, 0f, 0f, 0.27f, 14, 10).build(), 0.04f, 1.06f))
        parts.add(part(root, "helmet", MeshBuilder().color(main).sphere(0f, 0f, 0f, 0.31f, 14, 8).build(), 0.02f, 1.13f).also { it.sy = 0.78f })
        part(root, "brim", MeshBuilder().color(dark).box(0.2f, 0f, 0f, 0.12f, 0.05f, 0.5f).build(), 0.04f, 1.07f)
        part(root, "visor", MeshBuilder().color(light).box(0.2f, 0f, 0f, 0.08f, 0.1f, 0.34f).build(), 0.07f, 1.0f)

        val jet = part(root, "jetpack", MeshBuilder().color(dark).box(0f, 0f, 0f, 0.2f, 0.46f, 0.3f).build(), -0.33f, 0.72f)
        parts.add(jet)
        part(root, "nozL", MeshBuilder().color(0xFF3F525C.toInt()).cylinder(0f, -0.07f, 0f, 0.06f, 0.08f, 0.1f, 8, true, true).build(), -0.33f, 0.46f, -0.08f)
        part(root, "nozR", MeshBuilder().color(0xFF3F525C.toInt()).cylinder(0f, -0.07f, 0f, 0.06f, 0.08f, 0.1f, 8, true, true).build(), -0.33f, 0.46f, 0.08f)
        val flameMesh = MeshBuilder().color(0xFFFFB347.toInt()).cylinder(0f, -0.35f, 0f, 0.02f, 0.11f, 0.4f, 8, false, false).build()
        val flame = part(root, "flame", flameMesh, -0.33f, 0.4f)
        flame.emissive = 1f
        flame.visible = false

        val arm = part(root, "arm", MeshBuilder().color(main).box(0.12f, 0f, 0f, 0.3f, 0.14f, 0.14f).sphere(0.3f, 0f, 0f, 0.09f, 8, 6).build(), 0.06f, 0.82f, 0.3f)
        part(arm, "gun", MeshBuilder().color(0xFF2B2E33.toInt()).box(0.48f, 0.02f, 0f, 0.5f, 0.11f, 0.1f).build(), 0f, 0f)
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

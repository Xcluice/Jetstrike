package com.xcluice.jetstrike.engine

import com.xcluice.jetstrike.Skin
import kotlin.math.PI

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
        val parts: List<Node>,
        /** Weapon model, swapped with [setGun]. */
        val gun: Node? = null
    ) {
        private var gunKey = ""
        fun setGun(key: String) {
            if (key == gunKey) return
            gunKey = key
            gun?.mesh = Prefabs.gunMesh(key)
        }

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

    private val gunMeshes = HashMap<String, Mesh>()

    /** Weapon models along +x from the hand, sized per weapon so you can tell them apart at a glance. */
    fun gunMesh(key: String): Mesh = gunMeshes.getOrPut(key) {
        val dk = 0xFF2B2E33.toInt(); val md = 0xFF4A5058.toInt()
        val b = MeshBuilder()
        when (key) {
            "pistol" -> b.color(dk).box(0.2f, 0.02f, 0f, 0.3f, 0.09f, 0.09f).color(md).box(0.1f, -0.07f, 0f, 0.08f, 0.12f, 0.08f)
            "smg" -> b.color(dk).box(0.25f, 0.02f, 0f, 0.46f, 0.1f, 0.1f).color(md).box(0.18f, -0.12f, 0f, 0.07f, 0.2f, 0.08f)
            "rifle" -> b.color(dk).box(0.34f, 0.02f, 0f, 0.7f, 0.1f, 0.1f).color(md).box(0.12f, -0.07f, 0f, 0.2f, 0.12f, 0.09f).box(0.45f, -0.1f, 0f, 0.07f, 0.16f, 0.08f)
            "shotgun" -> b.color(dk).box(0.32f, 0.02f, 0f, 0.64f, 0.11f, 0.11f).color(0xFF8A5A33.toInt()).box(0.06f, -0.03f, 0f, 0.22f, 0.13f, 0.1f)
            "sniper" -> b.color(dk).box(0.5f, 0.02f, 0f, 1.0f, 0.08f, 0.08f).color(md).cylinder(0.3f, 0.08f, 0f, 0.05f, 0.05f, 0.2f, 8, true, true)
            "rocket" -> b.color(0xFF5C6B3A.toInt()).box(0.3f, 0.06f, 0f, 0.7f, 0.2f, 0.2f).color(dk).box(0.68f, 0.06f, 0f, 0.1f, 0.26f, 0.26f)
            "flame" -> b.color(0xFFB04A2A.toInt()).box(0.28f, 0.02f, 0f, 0.56f, 0.16f, 0.16f).color(dk).box(0.12f, 0.2f, 0f, 0.2f, 0.12f, 0.2f)
            "laser" -> b.color(0xFF3AA0C8.toInt()).box(0.28f, 0.02f, 0f, 0.56f, 0.12f, 0.12f).color(0xFFB8F8FF.toInt()).box(0.6f, 0.02f, 0f, 0.08f, 0.07f, 0.07f)
            else -> b.color(dk).box(0.25f, 0.02f, 0f, 0.5f, 0.1f, 0.1f)
        }
        b.build()
    }

    /**
     * A fighter built from the same numbers the 2D sprite uses (Hero.tw, th, hip, hr, limb, pack, squareHead) and the
     * same skin colours, plus that fighter's gear. Units: sprite pixels * 0.02, feet at y = 0, facing +x.
     */
    fun hero(sk: Skin, boss: Boolean = false): Soldier {
        val h = sk.h
        val S = Terrain3D.S
        val body = sk.c; val dark = sk.d; val light = sk.l
        val skinC = 0xFFE8B98F.toInt()
        val root = Node(); root.name = "hero"
        val parts = ArrayList<Node>()

        val hipY = h.hip * S
        val limbW = 0.13f * h.limb
        val torsoW = h.tw * 2f * S
        val torsoD = maxOf(0.26f, torsoW * 0.85f)
        val torsoH = h.th * S
        val torsoMid = (h.torsoBot + h.th / 2f) * S

        // legs pivot at the hip
        val leg = MeshBuilder().color(body).box(0f, -hipY / 2f, 0f, limbW, hipY, limbW).color(dark).box(0.02f, -hipY + 0.04f, 0f, limbW + 0.06f, 0.08f, limbW + 0.04f).build()
        val legBack = MeshBuilder().color(dark).box(0f, -hipY / 2f, 0f, limbW, hipY, limbW).color(0xFF1B2830.toInt()).box(0.02f, -hipY + 0.04f, 0f, limbW + 0.06f, 0.08f, limbW + 0.04f).build()
        val legL = part(root, "legL", legBack, 0f, hipY, -torsoD * 0.28f)
        val legR = part(root, "legR", leg, 0f, hipY, torsoD * 0.28f)
        parts.add(legR)

        // torso, belt and chest plate
        val tb = MeshBuilder().color(body).box(0f, 0f, 0f, torsoW, torsoH, torsoD)
            .color(dark).box(0f, -torsoH / 2f + 0.05f, 0f, torsoW + 0.02f, 0.1f, torsoD + 0.02f)
            .color(light).box(torsoW * 0.1f, torsoH * 0.18f, torsoD / 2f + 0.005f, torsoW * 0.6f, torsoH * 0.34f, 0.02f)
        val torso = part(root, "torso", tb.build(), 0f, torsoMid)
        parts.add(torso)

        // head: sphere, or a chunky block for the square-headed heroes, with the same cyan visor
        val hr = h.hr * S
        val headY = h.headCy * S
        val hb = MeshBuilder().color(body)
        if (h.squareHead) hb.box(0f, 0f, 0f, hr * 1.9f, hr * 1.84f, hr * 1.8f) else hb.sphere(0f, 0f, 0f, hr, 14, 10)
        hb.color(light).box(-hr * 0.2f, hr * 0.62f, 0f, hr * 0.9f, hr * 0.12f, hr * 0.9f)
        val head = part(root, "head", hb.build(), 0.02f, headY)
        parts.add(head)
        part(head, "visor", MeshBuilder().color(0xFF2B8CA3.toInt()).box(0f, 0f, 0f, hr * 0.5f, hr * 0.7f, hr * 1.3f)
            .color(0xFF8FE3F5.toInt()).box(0.012f, -0.01f, 0f, hr * 0.5f, hr * 0.5f, hr * 1.1f).build(), hr * 0.78f, 0f)

        // jetpack, bigger for the pilot
        val pk = h.pack
        val packW = 0.2f * pk
        val packX = -(torsoW / 2f + packW / 2f - 0.02f)
        val packH = torsoH * (0.9f + (pk - 1f) * 0.5f)
        val jb = MeshBuilder().color(0xFF3F525C.toInt()).box(0f, 0f, 0f, packW, packH, torsoD * 0.8f)
            .color(0xFF1B2830.toInt()).box(0f, -packH / 2f - 0.03f, torsoD * 0.22f, packW * 0.5f, 0.07f, 0.08f).box(0f, -packH / 2f - 0.03f, -torsoD * 0.22f, packW * 0.5f, 0.07f, 0.08f)
            .color(0xFFFF6F59.toInt()).box(0f, packH * 0.3f, 0f, packW * 0.9f, 0.05f, torsoD * 0.85f)
        parts.add(part(root, "jetpack", jb.build(), packX, torsoMid + 0.02f))
        val flame = part(root, "flame", MeshBuilder().color(0xFFFFB347.toInt())
            .cylinder(0f, -0.5f * pk, 0f, 0.015f, 0.1f * pk, 0.5f * pk, 8, false, false).build(), packX, torsoMid - packH / 2f - 0.02f)
        flame.emissive = 1f; flame.visible = false

        // gear per fighter
        when (sk.acc) {
            1 -> { // headband and shoulder bolts
                part(head, "band", MeshBuilder().color(0xFFFFE27A.toInt()).box(0f, hr * 0.35f, 0f, hr * 2.0f, hr * 0.3f, hr * 1.9f).build(), 0f, 0f)
            }
            2 -> { // helmet plate and heavy shoulder pads
                part(head, "plate", MeshBuilder().color(dark).box(0f, hr * 0.85f, 0f, hr * 2.0f, hr * 0.35f, hr * 1.95f).build(), 0f, 0f)
                part(root, "padF", MeshBuilder().color(dark).box(0f, 0f, 0f, 0.3f, 0.16f, 0.2f).build(), 0.04f, h.shoulder * S + 0.05f, torsoD / 2f + 0.06f)
                part(root, "padB", MeshBuilder().color(dark).box(0f, 0f, 0f, 0.3f, 0.16f, 0.2f).build(), 0.04f, h.shoulder * S + 0.05f, -torsoD / 2f - 0.06f)
            }
            3 -> { // hooded mohawk fin
                part(head, "fin", MeshBuilder().color(dark).cylinder(0f, 0f, 0f, hr * 0.5f, 0.01f, hr * 1.2f, 6, false, true).build(), -hr * 0.2f, hr * 0.7f)
            }
            4 -> { // medic cap and chest cross
                part(head, "cap", MeshBuilder().color(0xFFFFFFFF.toInt()).sphere(0f, 0f, 0f, hr * 1.04f, 12, 5).build(), 0f, hr * 0.28f).sy = 0.55f
                part(torso, "cross", MeshBuilder().color(0xFFE8465A.toInt()).box(0f, 0f, 0f, 0.16f, 0.05f, 0.02f).box(0f, 0f, 0f, 0.05f, 0.16f, 0.02f).build(), torsoW * 0.1f, torsoH * 0.18f, torsoD / 2f + 0.025f)
            }
            5 -> { // goggles
                part(head, "goggles", MeshBuilder().color(0xFFFFFFFF.toInt()).box(0f, 0f, 0f, hr * 0.42f, hr * 0.5f, hr * 1.6f).build(), hr * 0.62f, hr * 0.12f)
                part(head, "lens", MeshBuilder().color(0xFFFFE27A.toInt()).box(0f, 0f, 0f, 0.02f, hr * 0.38f, hr * 1.45f).build(), hr * 0.85f, hr * 0.12f).emissive = 0.4f
            }
            6 -> { // angular visor band and antenna
                part(head, "band", MeshBuilder().color(0xFF1B2830.toInt()).box(0f, 0f, 0f, hr * 2.02f, hr * 0.5f, hr * 1.95f).build(), 0f, hr * 0.1f)
                part(head, "ant", MeshBuilder().color(light).cylinder(0f, 0f, 0f, 0.02f, 0.02f, 0.3f, 6, true, false).build(), -hr * 0.4f, hr * 0.9f, hr * 0.5f)
            }
            7 -> { // cape
                part(root, "cape", MeshBuilder().color(dark).box(0f, -torsoH * 0.35f, 0f, 0.06f, torsoH * 1.1f, torsoW * 1.5f).build(), -torsoW / 2f - packW - 0.02f, torsoMid + torsoH * 0.35f)
            }
            8 -> { // overlord horns
                val horn = MeshBuilder().color(0xFFF2E3C4.toInt()).cylinder(0f, 0f, 0f, hr * 0.28f, 0.01f, hr * 1.5f, 8, false, true).build()
                part(head, "hornL", horn, -hr * 0.2f, hr * 0.7f, -hr * 0.6f).rz = 0.3f
                part(head, "hornR", horn, -hr * 0.2f, hr * 0.7f, hr * 0.6f).rz = 0.3f
            }
        }

        // gun arm pivots at the shoulder
        val armW = 0.14f * h.limb.coerceIn(0.8f, 1.4f)
        val armLen = (12f + (h.tw - 9.5f) * 0.5f) * S + 0.08f
        val arm = part(root, "arm", MeshBuilder().color(body).box(armLen / 2f, 0f, 0f, armLen, armW, armW)
            .color(light).sphere(armLen, 0f, 0f, armW * 0.7f, 8, 6).build(), 0.04f, h.shoulder * S, torsoD / 2f + armW / 2f)
        parts.add(arm)
        val gun = part(arm, "gun", gunMesh("pistol"), armLen - 0.02f, 0f, 0f)

        // hands-free back arm, just for depth
        part(root, "armBack", MeshBuilder().color(dark).box(0.12f, 0f, 0f, 0.28f, armW * 0.9f, armW * 0.9f).build(), 0.04f, h.shoulder * S - 0.04f, -torsoD / 2f - armW / 2f).rz = -0.5f

        val s = h.scale * (if (boss) 1f else 1f)
        root.sx = s; root.sy = s; root.sz = s
        return Soldier(root, legL, legR, arm, flame, parts, gun)
    }
}

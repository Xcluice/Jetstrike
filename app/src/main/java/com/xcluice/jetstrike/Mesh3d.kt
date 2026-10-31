package com.xcluice.jetstrike

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Vertex layout: x y z, nx ny nz, u v, r g b (11 floats, 3 vertices per triangle, no index buffer). */
const val VSTRIDE = 11

class Mesh3(val data: FloatArray, val count: Int) {
    var vbo = 0
}

/** Small triangle-soup builder with a transform stack, used for every 3D model and the terrain. */
class MeshB {
    private var buf = FloatArray(VSTRIDE * 3 * 1024)
    private var len = 0
    private var m = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
    private val stack = ArrayList<FloatArray>()
    private var cr = 1f
    private var cg = 1f
    private var cb = 1f

    fun color(c: Int) {
        cr = ((c shr 16) and 255) / 255f
        cg = ((c shr 8) and 255) / 255f
        cb = (c and 255) / 255f
    }

    fun push() { stack.add(m.copyOf()) }
    fun pop() { m = stack.removeAt(stack.size - 1) }

    private fun mul(b: FloatArray) {
        val a = m
        val r = FloatArray(12)
        for (i in 0 until 3) {
            for (j in 0 until 3) r[i * 4 + j] = a[i * 4] * b[j] + a[i * 4 + 1] * b[4 + j] + a[i * 4 + 2] * b[8 + j]
            r[i * 4 + 3] = a[i * 4] * b[3] + a[i * 4 + 1] * b[7] + a[i * 4 + 2] * b[11] + a[i * 4 + 3]
        }
        m = r
    }

    fun translate(x: Float, y: Float, z: Float) = mul(floatArrayOf(1f, 0f, 0f, x, 0f, 1f, 0f, y, 0f, 0f, 1f, z))
    fun scale(x: Float, y: Float, z: Float) = mul(floatArrayOf(x, 0f, 0f, 0f, 0f, y, 0f, 0f, 0f, 0f, z, 0f))
    fun rotZ(a: Float) {
        val c = cos(a); val s = sin(a)
        mul(floatArrayOf(c, -s, 0f, 0f, s, c, 0f, 0f, 0f, 0f, 1f, 0f))
    }
    fun rotY(a: Float) {
        val c = cos(a); val s = sin(a)
        mul(floatArrayOf(c, 0f, s, 0f, 0f, 1f, 0f, 0f, -s, 0f, c, 0f))
    }
    fun rotX(a: Float) {
        val c = cos(a); val s = sin(a)
        mul(floatArrayOf(1f, 0f, 0f, 0f, 0f, c, -s, 0f, 0f, s, c, 0f))
    }

    /** One vertex: position and normal go through the current transform. */
    fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float = 0f, w: Float = 0f) {
        if (len + VSTRIDE > buf.size) buf = buf.copyOf(buf.size * 2)
        val a = m
        val px = a[0] * x + a[1] * y + a[2] * z + a[3]
        val py = a[4] * x + a[5] * y + a[6] * z + a[7]
        val pz = a[8] * x + a[9] * y + a[10] * z + a[11]
        var qx = a[0] * nx + a[1] * ny + a[2] * nz
        var qy = a[4] * nx + a[5] * ny + a[6] * nz
        var qz = a[8] * nx + a[9] * ny + a[10] * nz
        val l = sqrt(qx * qx + qy * qy + qz * qz)
        if (l > 1e-6f) { qx /= l; qy /= l; qz /= l }
        buf[len++] = px; buf[len++] = py; buf[len++] = pz
        buf[len++] = qx; buf[len++] = qy; buf[len++] = qz
        buf[len++] = u; buf[len++] = w
        buf[len++] = cr; buf[len++] = cg; buf[len++] = cb
    }

    fun quad(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
             dx: Float, dy: Float, dz: Float, nx: Float, ny: Float, nz: Float) {
        v(ax, ay, az, nx, ny, nz); v(bx, by, bz, nx, ny, nz); v(cx, cy, cz, nx, ny, nz)
        v(ax, ay, az, nx, ny, nz); v(cx, cy, cz, nx, ny, nz); v(dx, dy, dz, nx, ny, nz)
    }

    fun box(w: Float, h: Float, d: Float) {
        val x = w / 2f; val y = h / 2f; val z = d / 2f
        quad(-x, -y, z, x, -y, z, x, y, z, -x, y, z, 0f, 0f, 1f)
        quad(x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 0f, 0f, -1f)
        quad(x, -y, z, x, -y, -z, x, y, -z, x, y, z, 1f, 0f, 0f)
        quad(-x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, -1f, 0f, 0f)
        quad(-x, y, z, x, y, z, x, y, -z, -x, y, -z, 0f, 1f, 0f)
        quad(-x, -y, -z, x, -y, -z, x, -y, z, -x, -y, z, 0f, -1f, 0f)
    }

    /** Cylinder along Y from -h/2 to h/2, radius rb at the bottom and rt at the top. */
    fun cylY(rb: Float, rt: Float, h: Float, seg: Int = 14, caps: Boolean = true) {
        val hh = h / 2f
        val slope = if (h > 1e-4f) (rb - rt) / h else 0f
        for (i in 0 until seg) {
            val a0 = (2.0 * PI * i / seg).toFloat()
            val a1 = (2.0 * PI * (i + 1) / seg).toFloat()
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            v(rb * c0, -hh, rb * s0, c0, slope, s0); v(rb * c1, -hh, rb * s1, c1, slope, s1); v(rt * c1, hh, rt * s1, c1, slope, s1)
            v(rb * c0, -hh, rb * s0, c0, slope, s0); v(rt * c1, hh, rt * s1, c1, slope, s1); v(rt * c0, hh, rt * s0, c0, slope, s0)
            if (caps) {
                if (rt > 0.01f) { v(0f, hh, 0f, 0f, 1f, 0f); v(rt * c1, hh, rt * s1, 0f, 1f, 0f); v(rt * c0, hh, rt * s0, 0f, 1f, 0f) }
                if (rb > 0.01f) { v(0f, -hh, 0f, 0f, -1f, 0f); v(rb * c0, -hh, rb * s0, 0f, -1f, 0f); v(rb * c1, -hh, rb * s1, 0f, -1f, 0f) }
            }
        }
    }

    fun cone(r: Float, h: Float, seg: Int = 10) = cylY(r, 0f, h, seg)

    /** Sphere around the origin; thetaMax below PI gives a cap (hood, dome). */
    fun sphere(r: Float, segW: Int = 12, segH: Int = 8, thetaMax: Float = PI.toFloat()) {
        for (i in 0 until segH) {
            val t0 = thetaMax * i / segH
            val t1 = thetaMax * (i + 1) / segH
            for (j in 0 until segW) {
                val p0 = (2.0 * PI * j / segW).toFloat()
                val p1 = (2.0 * PI * (j + 1) / segW).toFloat()
                val ax = sin(t0) * cos(p0); val ay = cos(t0); val az = sin(t0) * sin(p0)
                val bx = sin(t0) * cos(p1); val bz = sin(t0) * sin(p1)
                val cx = sin(t1) * cos(p1); val cy = cos(t1); val cz = sin(t1) * sin(p1)
                val dx = sin(t1) * cos(p0); val dz = sin(t1) * sin(p0)
                v(r * ax, r * ay, r * az, ax, ay, az); v(r * bx, r * ay, r * bz, bx, ay, bz); v(r * cx, r * cy, r * cz, cx, cy, cz)
                v(r * ax, r * ay, r * az, ax, ay, az); v(r * cx, r * cy, r * cz, cx, cy, cz); v(r * dx, r * cy, r * dz, dx, cy, dz)
            }
        }
    }

    /** Ring in the XY plane. */
    fun torus(big: Float, tube: Float, segMaj: Int = 20, segMin: Int = 8) {
        for (i in 0 until segMaj) {
            val a0 = (2.0 * PI * i / segMaj).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segMaj).toFloat()
            for (j in 0 until segMin) {
                val b0 = (2.0 * PI * j / segMin).toFloat()
                val b1 = (2.0 * PI * (j + 1) / segMin).toFloat()
                fun px(a: Float, b: Float) = (big + tube * cos(b)) * cos(a)
                fun py(a: Float, b: Float) = (big + tube * cos(b)) * sin(a)
                fun pz(b: Float) = tube * sin(b)
                fun nx(a: Float, b: Float) = cos(b) * cos(a)
                fun ny(a: Float, b: Float) = cos(b) * sin(a)
                v(px(a0, b0), py(a0, b0), pz(b0), nx(a0, b0), ny(a0, b0), sin(b0))
                v(px(a1, b0), py(a1, b0), pz(b0), nx(a1, b0), ny(a1, b0), sin(b0))
                v(px(a1, b1), py(a1, b1), pz(b1), nx(a1, b1), ny(a1, b1), sin(b1))
                v(px(a0, b0), py(a0, b0), pz(b0), nx(a0, b0), ny(a0, b0), sin(b0))
                v(px(a1, b1), py(a1, b1), pz(b1), nx(a1, b1), ny(a1, b1), sin(b1))
                v(px(a0, b1), py(a0, b1), pz(b1), nx(a0, b1), ny(a0, b1), sin(b1))
            }
        }
    }

    /** Rounded rectangle in XY extruded along Z (rounded silhouettes, like the 2D sprites). */
    fun rrExtrude(x0: Float, y0: Float, x1: Float, y1: Float, rad0: Float, z0: Float, z1: Float, steps: Int = 3) {
        val w = x1 - x0
        val h = y1 - y0
        val rad = min(rad0, min(w, h) / 2f - 0.001f)
        if (rad <= 0.05f) {
            push(); translate((x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f); box(w, h, z1 - z0); pop()
            return
        }
        val px = ArrayList<Float>(); val py = ArrayList<Float>(); val nx = ArrayList<Float>(); val ny = ArrayList<Float>()
        val cxs = floatArrayOf(x1 - rad, x1 - rad, x0 + rad, x0 + rad)
        val cys = floatArrayOf(y0 + rad, y1 - rad, y1 - rad, y0 + rad)
        val st = floatArrayOf(-90f, 0f, 90f, 180f)
        for (k in 0 until 4) for (s in 0..steps) {
            val a = ((st[k] + 90f * s / steps) * PI / 180.0).toFloat()
            val c = cos(a); val sn = sin(a)
            px.add(cxs[k] + rad * c); py.add(cys[k] + rad * sn); nx.add(c); ny.add(sn)
        }
        val n = px.size
        val mx = (x0 + x1) / 2f
        val my = (y0 + y1) / 2f
        for (i in 0 until n) {
            val j = (i + 1) % n
            v(mx, my, z1, 0f, 0f, 1f); v(px[i], py[i], z1, 0f, 0f, 1f); v(px[j], py[j], z1, 0f, 0f, 1f)
            v(mx, my, z0, 0f, 0f, -1f); v(px[j], py[j], z0, 0f, 0f, -1f); v(px[i], py[i], z0, 0f, 0f, -1f)
            v(px[i], py[i], z0, nx[i], ny[i], 0f); v(px[j], py[j], z0, nx[j], ny[j], 0f); v(px[j], py[j], z1, nx[j], ny[j], 0f)
            v(px[i], py[i], z0, nx[i], ny[i], 0f); v(px[j], py[j], z1, nx[j], ny[j], 0f); v(px[i], py[i], z1, nx[i], ny[i], 0f)
        }
    }

    /** Thick rod between two points (arms, antennae). */
    fun rod(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r: Float, seg: Int = 8) {
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val l = sqrt(dx * dx + dy * dy + dz * dz)
        if (l < 1e-4f) return
        push()
        translate((ax + bx) / 2f, (ay + by) / 2f, (az + bz) / 2f)
        // rotate +Y onto the direction
        val ux = dx / l; val uy = dy / l; val uz = dz / l
        val ang = kotlin.math.acos(uy.coerceIn(-1f, 1f))
        val axx = uz; val axz = -ux // axis = Y x dir
        val al = sqrt(axx * axx + axz * axz)
        if (al > 1e-5f) rotAxis(axx / al, 0f, axz / al, ang)
        else if (uy < 0f) rotX(PI.toFloat())
        cylY(r, r, l, seg)
        pop()
    }

    private fun rotAxis(x: Float, y: Float, z: Float, a: Float) {
        val c = cos(a); val s = sin(a); val t = 1f - c
        mul(floatArrayOf(
            t * x * x + c, t * x * y - s * z, t * x * z + s * y, 0f,
            t * x * y + s * z, t * y * y + c, t * y * z - s * x, 0f,
            t * x * z - s * y, t * y * z + s * x, t * z * z + c, 0f))
    }

    fun build(): Mesh3 = Mesh3(buf.copyOf(len), len / VSTRIDE)
}

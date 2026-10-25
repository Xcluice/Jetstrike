package com.xcluice.jetstrike

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue

const val TCP_PORT = 47777
const val UDP_PORT = 47778
const val MAGIC = "JETSTRIKE1"

// client -> host
const val MSG_HELLO = 1
const val MSG_INPUT = 2

// host -> client
const val MSG_WELCOME = 10
const val MSG_LOBBY = 11
const val MSG_START = 12
const val MSG_SNAP = 13
const val MSG_REJECT = 14

// local pseudo messages delivered to the game thread
const val MSG_DISCONNECTED = 100
const val MSG_CLIENT_LEFT = 101

class HostInfo(val ip: String, val rules: Int, val players: Int, val max: Int) {
    @Volatile var seen: Long = System.currentTimeMillis()
}

class RemoteIn {
    @Volatile var mx = 0f
    @Volatile var jet = false
    @Volatile var fire = false
    @Volatile var aim = 0f
    @Volatile var gCnt = 0
    @Volatile var sCnt = 0
    @Volatile var sVal = 0
    @Volatile var rCnt = 0
}

class Msg(val type: Int, val data: ByteArray)

/** One TCP connection. Frames are [int length][byte type][payload]. */
class Conn(val sock: Socket) {
    private val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 8192))
    val inp = DataInputStream(BufferedInputStream(sock.getInputStream(), 8192))
    private val q = LinkedBlockingQueue<ByteArray>(24)
    @Volatile var alive = true
    @Volatile var playerId = -1
    @Volatile var skin = 0
    @Volatile var name = "Player"
    val rin = RemoteIn()

    init {
        Thread({
            try {
                while (alive) {
                    val f = q.take()
                    out.writeInt(f.size)
                    out.write(f)
                    out.flush()
                }
            } catch (e: Exception) {
                alive = false
            }
        }, "jetstrike-writer").also { it.isDaemon = true; it.start() }
    }

    fun send(frame: ByteArray, droppable: Boolean) {
        if (!alive) return
        if (!q.offer(frame) && droppable) {
            q.poll()
            q.offer(frame)
        }
    }

    fun close() {
        alive = false
        try { sock.close() } catch (e: Exception) { }
    }
}

fun frame(type: Int, build: (DataOutputStream) -> Unit): ByteArray {
    val bo = ByteArrayOutputStream(256)
    val o = DataOutputStream(bo)
    o.writeByte(type)
    build(o)
    o.flush()
    return bo.toByteArray()
}

class Net(private val ctx: Context) {
    val inbox = ConcurrentLinkedQueue<Msg>()
    val hosts = ConcurrentHashMap<String, HostInfo>()
    val clients = CopyOnWriteArrayList<Conn>()
    @Volatile var started = false
    @Volatile var hosting = false
    @Volatile var listening = false
    @Volatile var hostRules = 0
    @Volatile var hostDiff = 1
    private var server: ServerSocket? = null
    private var toHost: Conn? = null
    private var mcLock: WifiManager.MulticastLock? = null
    private var listenSock: DatagramSocket? = null

    fun maxPlayers(): Int = if (hostRules == 0) 3 else 4

    /* ---------- host ---------- */
    fun startHosting(rules: Int, diff: Int): Boolean {
        shutdown()
        hostRules = rules
        hostDiff = diff
        started = false
        val ss = try {
            ServerSocket().also {
                it.reuseAddress = true
                it.bind(InetSocketAddress(TCP_PORT))
            }
        } catch (e: Exception) {
            return false
        }
        server = ss
        hosting = true
        Thread({ acceptLoop(ss) }, "jetstrike-accept").also { it.isDaemon = true; it.start() }
        Thread({ beaconLoop() }, "jetstrike-beacon").also { it.isDaemon = true; it.start() }
        return true
    }

    fun activeClients(): List<Conn> = clients.filter { it.alive }

    private fun acceptLoop(ss: ServerSocket) {
        while (hosting) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            Thread({ handleClient(s) }, "jetstrike-client").also { it.isDaemon = true; it.start() }
        }
    }

    private fun handleClient(s: Socket) {
        val c = try {
            s.tcpNoDelay = true
            s.soTimeout = 4000
            Conn(s)
        } catch (e: Exception) {
            try { s.close() } catch (x: Exception) { }
            return
        }
        try {
            val len = c.inp.readInt()
            val buf = ByteArray(len)
            c.inp.readFully(buf)
            if (buf.isEmpty() || (buf[0].toInt() and 0xFF) != MSG_HELLO) { c.close(); return }
            try {
                val dd = DataInputStream(buf.inputStream(1, buf.size - 1))
                c.skin = dd.readUnsignedByte()
                c.name = dd.readUTF().take(12)
            } catch (e: Exception) { }
            if (started || clients.count { it.alive } + 1 >= maxPlayers()) {
                c.send(frame(MSG_REJECT) { }, false)
                Thread.sleep(200)
                c.close()
                return
            }
            s.soTimeout = 0
            clients.add(c)
            c.send(frame(MSG_WELCOME) {
                it.writeByte(clients.size + 1); it.writeByte(hostRules); it.writeByte(hostDiff)
            }, false)
            broadcastLobby()
            while (c.alive) {
                val n = c.inp.readInt()
                if (n < 1 || n > 4096) break
                val b = ByteArray(n)
                c.inp.readFully(b)
                if ((b[0].toInt() and 0xFF) == MSG_INPUT) parseInput(c, b)
            }
        } catch (e: Exception) {
            // connection ended
        }
        c.close()
        clients.remove(c)
        if (c.playerId >= 0) inbox.add(Msg(MSG_CLIENT_LEFT, byteArrayOf(c.playerId.toByte())))
        if (!started) broadcastLobby()
    }

    private fun parseInput(c: Conn, b: ByteArray) {
        val d = DataInputStream(b.inputStream(1, b.size - 1))
        val r = c.rin
        r.mx = d.readFloat()
        val fl = d.readUnsignedByte()
        r.jet = (fl and 1) != 0
        r.fire = (fl and 2) != 0
        r.aim = d.readFloat()
        r.gCnt = d.readInt()
        r.sCnt = d.readInt()
        r.sVal = d.readUnsignedByte()
        r.rCnt = d.readInt()
    }

    fun broadcastLobby() {
        val n = clients.count { it.alive } + 1
        val f = frame(MSG_LOBBY) { it.writeByte(n); it.writeByte(hostRules); it.writeByte(hostDiff) }
        for (c in clients) c.send(f, false)
    }

    fun sendStart(c: Conn, rules: Int, diff: Int, id: Int, humans: Int, skins: IntArray, names: List<String>) {
        c.send(frame(MSG_START) {
            it.writeByte(rules); it.writeByte(diff); it.writeByte(id); it.writeByte(humans)
            for (k in 0 until humans) { it.writeByte(skins[k]); it.writeUTF(names[k]) }
        }, false)
    }

    fun sendSnapshot(bytes: ByteArray) {
        for (c in clients) c.send(bytes, true)
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = ArrayList<InetAddress>()
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en != null && en.hasMoreElements()) {
                val ni = en.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast
                    if (b != null) out.add(b)
                }
            }
        } catch (e: Exception) { }
        try { out.add(InetAddress.getByName("255.255.255.255")) } catch (e: Exception) { }
        return out
    }

    private fun beaconLoop() {
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket()
            sock.broadcast = true
            while (hosting) {
                if (!started) {
                    val msg = "$MAGIC|${hostRules}|${clients.count { it.alive } + 1}|${maxPlayers()}".toByteArray()
                    for (a in broadcastTargets()) {
                        try { sock.send(DatagramPacket(msg, msg.size, a, UDP_PORT)) } catch (e: Exception) { }
                    }
                }
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            // stopped
        } finally {
            try { sock?.close() } catch (e: Exception) { }
        }
    }

    fun localIp(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en != null && en.hasMoreElements()) {
                val ni = en.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    val a = ia.address
                    if (a is java.net.Inet4Address && a.isSiteLocalAddress) return a.hostAddress ?: ""
                }
            }
        } catch (e: Exception) { }
        return ""
    }

    /* ---------- client ---------- */
    fun startListening() {
        if (listening) return
        listening = true
        hosts.clear()
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            mcLock = wm?.createMulticastLock("jetstrike")?.also { it.setReferenceCounted(false); it.acquire() }
        } catch (e: Exception) { }
        Thread({
            var sock: DatagramSocket? = null
            try {
                sock = DatagramSocket(null)
                sock.reuseAddress = true
                sock.broadcast = true
                sock.bind(InetSocketAddress(UDP_PORT))
                sock.soTimeout = 800
                listenSock = sock
                val buf = ByteArray(256)
                while (listening) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        sock.receive(p)
                        val s = String(p.data, 0, p.length)
                        val parts = s.split("|")
                        if (parts.size >= 4 && parts[0] == MAGIC) {
                            val ip = p.address.hostAddress ?: continue
                            val info = HostInfo(ip, parts[1].toIntOrNull() ?: 0, parts[2].toIntOrNull() ?: 1, parts[3].toIntOrNull() ?: 4)
                            hosts[ip] = info
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // keep listening
                    }
                }
            } catch (e: Exception) {
                // stopped
            } finally {
                try { sock?.close() } catch (e: Exception) { }
            }
        }, "jetstrike-listen").also { it.isDaemon = true; it.start() }
    }

    fun stopListening() {
        listening = false
        try { listenSock?.close() } catch (e: Exception) { }
        try { mcLock?.release() } catch (e: Exception) { }
        mcLock = null
    }

    fun liveHosts(): List<HostInfo> {
        val now = System.currentTimeMillis()
        val l = ArrayList<HostInfo>()
        for ((k, h) in hosts) {
            if (now - h.seen > 4000) hosts.remove(k) else l.add(h)
        }
        return l.sortedBy { it.ip }
    }

    fun connect(ip: String, skin: Int, name: String) {
        stopListening()
        Thread({
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, TCP_PORT), 3000)
                s.tcpNoDelay = true
                val c = Conn(s)
                toHost = c
                c.send(frame(MSG_HELLO) { it.writeByte(skin); it.writeUTF(name) }, false)
                while (c.alive) {
                    val n = c.inp.readInt()
                    if (n < 1 || n > (1 shl 20)) break
                    val b = ByteArray(n)
                    c.inp.readFully(b)
                    inbox.add(Msg(b[0].toInt() and 0xFF, b.copyOfRange(1, b.size)))
                }
            } catch (e: Exception) {
                // fall through to disconnect
            }
            toHost?.close()
            toHost = null
            inbox.add(Msg(MSG_DISCONNECTED, ByteArray(0)))
        }, "jetstrike-connect").also { it.isDaemon = true; it.start() }
    }

    fun sendToHost(f: ByteArray) {
        toHost?.send(f, true)
    }

    fun shutdown() {
        hosting = false
        started = false
        stopListening()
        try { server?.close() } catch (e: Exception) { }
        server = null
        for (c in clients) c.close()
        clients.clear()
        toHost?.close()
        toHost = null
        inbox.clear()
    }
}

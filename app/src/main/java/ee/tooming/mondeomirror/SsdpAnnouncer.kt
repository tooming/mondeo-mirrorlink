package ee.tooming.mondeomirror

import android.net.Network
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import kotlin.concurrent.thread

/**
 * Advertises this device as a MirrorLink Server (ETSI TS 103 544-12) via SSDP
 * on the given network, and answers M-SEARCH queries. Phase 1: unsigned
 * device description, just to observe whether the car reacts at all.
 */
class SsdpAnnouncer(
    private val network: Network,
    private val localIp: String,
    private val httpPort: Int,
    private val uuid: String
) {
    private val group = InetAddress.getByName("239.255.255.250")
    private val ssdpPort = 1900
    private val deviceType = "urn:schemas-upnp-org:device:TmServerDevice:1"
    private val location get() = "http://$localIp:$httpPort/description.xml"

    private var socket: MulticastSocket? = null
    @Volatile private var running = false
    private var announceThread: Thread? = null
    private var listenThread: Thread? = null

    fun start() {
        if (running) return
        running = true

        val s = MulticastSocket(ssdpPort)
        network.bindSocket(s)
        s.reuseAddress = true
        s.timeToLive = 4

        val iface = try {
            NetworkInterface.getByInetAddress(InetAddress.getByName(localIp))
        } catch (e: Exception) {
            null
        }
        s.joinGroup(InetSocketAddress(group, ssdpPort), iface)
        socket = s

        LogBus.log("SSDP", "Joined 239.255.255.250:1900 on $localIp (iface=${iface?.name}), LOCATION=$location")

        announceThread = thread(name = "ssdp-announce") { announceLoop() }
        listenThread = thread(name = "ssdp-listen") { listenLoop() }
    }

    fun stop() {
        running = false
        try {
            socket?.leaveGroup(InetSocketAddress(group, ssdpPort), null)
        } catch (_: Exception) {
        }
        socket?.close()
        announceThread?.interrupt()
        listenThread?.interrupt()
        LogBus.log("SSDP", "Stopped")
    }

    private fun targets(): List<Pair<String, String>> = listOf(
        "upnp:rootdevice" to "uuid:$uuid::upnp:rootdevice",
        "uuid:$uuid" to "uuid:$uuid",
        deviceType to "uuid:$uuid::$deviceType"
    )

    private fun announceLoop() {
        sendAliveBurst()
        while (running) {
            try {
                Thread.sleep(30_000)
                if (running) sendAliveBurst()
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                LogBus.log("SSDP", "announce error: ${e.message}")
            }
        }
    }

    private fun sendAliveBurst() {
        for ((nt, usn) in targets()) sendNotify(nt, usn)
        LogBus.log("SSDP", "Sent ssdp:alive burst")
    }

    private fun sendNotify(nt: String, usn: String) {
        val msg = "NOTIFY * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: $location\r\n" +
            "NT: $nt\r\n" +
            "NTS: ssdp:alive\r\n" +
            "SERVER: Android UPnP/1.0 MondeoMirror/0.1\r\n" +
            "USN: $usn\r\n\r\n"
        val data = msg.toByteArray(Charsets.US_ASCII)
        try {
            socket?.send(DatagramPacket(data, data.size, group, ssdpPort))
        } catch (e: Exception) {
            LogBus.log("SSDP", "notify send failed: ${e.message}")
        }
    }

    private fun listenLoop() {
        val buf = ByteArray(4096)
        while (running) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket?.receive(packet)
                val text = String(packet.data, 0, packet.length, Charsets.US_ASCII)
                if (text.startsWith("M-SEARCH", ignoreCase = true)) {
                    LogBus.log("SSDP", "M-SEARCH from ${packet.address.hostAddress}:${packet.port}\n$text")
                    respondToSearch(packet.address, packet.port)
                }
            } catch (e: Exception) {
                if (running) LogBus.log("SSDP", "listen error: ${e.message}")
            }
        }
    }

    private fun respondToSearch(addr: InetAddress, port: Int) {
        for ((st, usn) in targets()) {
            val msg = "HTTP/1.1 200 OK\r\n" +
                "CACHE-CONTROL: max-age=1800\r\n" +
                "EXT:\r\n" +
                "LOCATION: $location\r\n" +
                "SERVER: Android UPnP/1.0 MondeoMirror/0.1\r\n" +
                "ST: $st\r\n" +
                "USN: $usn\r\n\r\n"
            val data = msg.toByteArray(Charsets.US_ASCII)
            try {
                socket?.send(DatagramPacket(data, data.size, addr, port))
            } catch (e: Exception) {
                LogBus.log("SSDP", "search reply failed: ${e.message}")
            }
        }
        LogBus.log("SSDP", "Replied to M-SEARCH")
    }
}

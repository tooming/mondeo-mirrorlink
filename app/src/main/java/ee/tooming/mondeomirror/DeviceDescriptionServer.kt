package ee.tooming.mondeomirror

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Serves the UPnP device description XML for the TmServerDevice (ETSI TS
 * 103 544-12 clause 5). Phase 1 leaves it unsigned - the spec requires an
 * XML signature verified via the Device Attestation Protocol, which needs a
 * CCC-issued certificate we don't have. Serving it unsigned tells us whether
 * the car's implementation actually enforces that at runtime.
 *
 * Bound directly to the USB interface's own address (rather than
 * Network.bindSocket, which has no ServerSocket overload) so it only
 * listens on that link, not on Wi-Fi/mobile as well.
 */
class DeviceDescriptionServer(
    private val localIp: String,
    private val uuid: String,
    private val friendlyName: String = "Mondeo MirrorLink Test"
) {
    var port: Int = 0
        private set

    private var serverSocket: ServerSocket? = null
    @Volatile private var running = false
    private var acceptThread: Thread? = null

    fun start() {
        val s = ServerSocket()
        s.bind(InetSocketAddress(InetAddress.getByName(localIp), 0))
        serverSocket = s
        port = s.localPort
        running = true
        LogBus.log("HTTP", "Serving device description on port $port")
        acceptThread = thread(name = "ml-http-accept") { acceptLoop() }
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        acceptThread?.interrupt()
        LogBus.log("HTTP", "Stopped")
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val client = serverSocket?.accept() ?: break
                thread(name = "ml-http-client") { handleClient(client) }
            } catch (e: Exception) {
                if (running) LogBus.log("HTTP", "accept error: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            try {
                val input = sock.getInputStream().bufferedReader(Charsets.US_ASCII)
                val requestLine = input.readLine() ?: return
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }
                LogBus.log("HTTP", "Request from ${sock.inetAddress.hostAddress}: $requestLine")

                val body = deviceDescriptionXml().toByteArray(Charsets.UTF_8)
                val output = sock.getOutputStream()
                val headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/xml; charset=\"utf-8\"\r\n" +
                    "Content-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n"
                output.write(headers.toByteArray(Charsets.US_ASCII))
                output.write(body)
                output.flush()
            } catch (e: Exception) {
                LogBus.log("HTTP", "client error: ${e.message}")
            }
        }
    }

    private fun deviceDescriptionXml(): String = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0">
    <specVersion>
        <major>1</major>
        <minor>0</minor>
    </specVersion>
    <device>
        <deviceType>urn:schemas-upnp-org:device:TmServerDevice:1</deviceType>
        <friendlyName>$friendlyName</friendlyName>
        <manufacturer>tooming</manufacturer>
        <manufacturerURL>https://github.com/</manufacturerURL>
        <modelDescription>Phase 1 MirrorLink discovery probe</modelDescription>
        <modelName>MondeoMirror</modelName>
        <modelNumber>0.1</modelNumber>
        <UDN>uuid:$uuid</UDN>
        <serviceList>
            <service>
                <serviceType>urn:schemas-upnp-org:service:TmApplicationServer:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:TmApplicationServer1</serviceId>
                <SCPDURL>/scpd/app.xml</SCPDURL>
                <controlURL>/control/app</controlURL>
                <eventSubURL>/event/app</eventSubURL>
            </service>
            <service>
                <serviceType>urn:schemas-upnp-org:service:TmClientProfile:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:TmClientProfile1</serviceId>
                <SCPDURL>/scpd/profile.xml</SCPDURL>
                <controlURL>/control/profile</controlURL>
                <eventSubURL>/event/profile</eventSubURL>
            </service>
            <service>
                <serviceType>urn:schemas-upnp-org:service:TmNotificationServer:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:TmNotificationServer1</serviceId>
                <SCPDURL>/scpd/notify.xml</SCPDURL>
                <controlURL>/control/notify</controlURL>
                <eventSubURL>/event/notify</eventSubURL>
            </service>
        </serviceList>
    </device>
</root>
"""
}

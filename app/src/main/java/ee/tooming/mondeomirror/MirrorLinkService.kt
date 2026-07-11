package ee.tooming.mondeomirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import java.net.Inet4Address
import java.util.UUID

/**
 * Foreground service that waits for a USB (TRANSPORT_USB) network - i.e. the
 * phone's own USB tethering interface once plugged into the car and enabled
 * in Settings - and then starts advertising this device as a MirrorLink
 * Server on it (SsdpAnnouncer + DeviceDescriptionServer).
 */
class MirrorLinkService : Service() {

    private lateinit var connectivityManager: ConnectivityManager
    private var descriptionServer: DeviceDescriptionServer? = null
    private var announcer: SsdpAnnouncer? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val uuid: String by lazy { getOrCreateUuid() }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for USB network…"))
        LogBus.log("SVC", "MirrorLinkService starting, uuid=$uuid")
        registerUsbNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        stopServing()
        LogBus.log("SVC", "MirrorLinkService stopped")
    }

    private fun registerUsbNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_USB)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                LogBus.log("NET", "USB network available: $network")
                tryStart(network)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (descriptionServer == null) tryStart(network, linkProperties)
            }

            override fun onLost(network: Network) {
                LogBus.log("NET", "USB network lost")
                stopServing()
                updateNotification("Waiting for USB network…")
            }
        }
        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)
        LogBus.log(
            "SVC",
            "Waiting for TRANSPORT_USB network. Plug into the car and enable USB tethering in Settings."
        )
    }

    private fun tryStart(network: Network, linkPropsHint: LinkProperties? = null) {
        val linkProps = linkPropsHint ?: connectivityManager.getLinkProperties(network)
        val ip = linkProps?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address }
            ?.hostAddress
        if (ip == null) {
            LogBus.log("NET", "USB network has no IPv4 yet, waiting for link properties update")
            return
        }
        startServing(network, ip)
    }

    private fun startServing(network: Network, ip: String) {
        if (descriptionServer != null) return
        LogBus.log("NET", "Using IP $ip on $network")

        val server = DeviceDescriptionServer(ip, uuid)
        server.start()
        descriptionServer = server

        val ann = SsdpAnnouncer(network, ip, server.port, uuid)
        ann.start()
        announcer = ann

        updateNotification("Announcing MirrorLink on $ip:${server.port}")
    }

    private fun stopServing() {
        announcer?.stop()
        announcer = null
        descriptionServer?.stop()
        descriptionServer = null
    }

    private fun getOrCreateUuid(): String {
        val prefs = getSharedPreferences("mirrorlink", MODE_PRIVATE)
        var id = prefs.getString("uuid", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("uuid", id).apply()
        }
        return id
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "mirrorlink_status"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "MirrorLink status", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("Mondeo MirrorLink")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val NOTIFICATION_ID = 1
    }
}

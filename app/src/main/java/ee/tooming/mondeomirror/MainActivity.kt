package ee.tooming.mondeomirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val logListener: (String) -> Unit = { line ->
        runOnUiThread {
            logView.append(line + "\n")
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }

        val startButton = Button(this).apply { text = "Start MirrorLink announce" }
        val stopButton = Button(this).apply { text = "Stop" }
        val tetherButton = Button(this).apply { text = "Open USB tethering settings" }

        logView = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(logView)
        }

        startButton.setOnClickListener {
            startForegroundService(Intent(this, MirrorLinkService::class.java))
        }
        stopButton.setOnClickListener {
            stopService(Intent(this, MirrorLinkService::class.java))
        }
        tetherButton.setOnClickListener {
            try {
                startActivity(Intent("android.settings.TETHER_SETTINGS"))
            } catch (e: Exception) {
                LogBus.log("UI", "No direct tethering settings screen: ${e.message}")
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (_: Exception) {
                }
            }
        }

        root.addView(startButton)
        root.addView(stopButton)
        root.addView(tetherButton)
        root.addView(scrollView)
        setContentView(root)

        logView.text = LogBus.snapshot().joinToString("\n")
        LogBus.addListener(logListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        LogBus.removeListener(logListener)
    }
}

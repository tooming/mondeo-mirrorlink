package ee.tooming.mondeomirror

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

object LogBus {
    private val lines = Collections.synchronizedList(mutableListOf<String>())
    private val listeners = Collections.synchronizedList(mutableListOf<(String) -> Unit>())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, msg: String) {
        val line = "${timeFormat.format(Date())} [$tag] $msg"
        Log.d("MondeoMirror", line)
        lines.add(line)
        if (lines.size > 1000) lines.removeAt(0)
        synchronized(listeners) { listeners.toList() }.forEach { it(line) }
    }

    fun snapshot(): List<String> = lines.toList()

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}

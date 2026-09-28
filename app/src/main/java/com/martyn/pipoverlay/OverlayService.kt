package com.martyn.pipoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OverlayService : Service() {

    companion object {
        const val PORT = 5002
    }

    private lateinit var controller: PipController
    private var server: ApiServer? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("pip", "PIP Overlay", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, "pip")
            .setContentTitle("PIP Overlay running")
            .setContentText("Listening on port $PORT")
            .setSmallIcon(R.drawable.ic_launcher)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        controller = PipController(this)
        try {
            server = ApiServer().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        } catch (_: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        server?.stop()
        main.post { controller.hide() }
        super.onDestroy()
    }

    private fun <T> onMain(block: () -> T): T? {
        val ref = AtomicReference<T?>()
        val latch = CountDownLatch(1)
        main.post {
            try { ref.set(block()) } finally { latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        return ref.get()
    }

    private inner class ApiServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            val files = HashMap<String, String>()
            if (session.method == Method.POST || session.method == Method.PUT) {
                try { session.parseBody(files) } catch (_: Exception) {}
            }
            val p = JSONObject()
            session.parms.forEach { (k, v) -> p.put(k, v) }
            files["postData"]?.let { raw ->
                try {
                    val j = JSONObject(raw)
                    j.keys().forEach { k -> p.put(k, j.get(k)) }
                } catch (_: Exception) {}
            }

            return when (session.uri.trimEnd('/')) {
                "/pip/show" -> {
                    val req = PipRequest(
                        url = p.optString("url", ""),
                        position = p.optString("position", "top_right"),
                        widthPercent = p.optInt("width_percent", 25),
                        marginDp = p.optInt("margin", 24),
                        timeoutSec = p.optInt("timeout", 0),
                        title = p.optString("title", ""),
                        mute = p.optString("mute", "true") != "false"
                    )
                    val err = onMain { controller.show(req) ?: "" }
                    if (err.isNullOrEmpty()) json(Response.Status.OK, "ok")
                    else json(Response.Status.BAD_REQUEST, err)
                }
                "/pip/hide" -> {
                    onMain { controller.hide() }
                    json(Response.Status.OK, "ok")
                }
                "/status", "" -> {
                    val showing = onMain { controller.isShowing } ?: false
                    val url = onMain { controller.url } ?: ""
                    newFixedLengthResponse(
                        Response.Status.OK, "application/json",
                        JSONObject().put("showing", showing).put("url", url).toString()
                    )
                }
                else -> json(Response.Status.NOT_FOUND, "not found")
            }
        }

        private fun json(status: Response.Status, msg: String) =
            newFixedLengthResponse(
                status, "application/json",
                JSONObject().put(if (status == Response.Status.OK) "status" else "error", msg).toString()
            )
    }
}

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.martyn.pipoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

data class PipRequest(
    val url: String,
    val position: String = "top_right",
    val widthPercent: Int = 25,
    val marginDp: Int = 24,
    val timeoutSec: Int = 0,
    val title: String = "",
    val mute: Boolean = true
)

/** Must be used from the main thread only. */
class PipController(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var player: ExoPlayer? = null
    private var errorView: TextView? = null
    private var currentUrl: String? = null
    private var hideRunnable: Runnable? = null

    val isShowing: Boolean get() = root != null
    val url: String? get() = currentUrl

    /** Returns null on success, or an error message. */
    fun show(r: PipRequest): String? {
        if (!Settings.canDrawOverlays(ctx)) return "Overlay permission not granted"
        if (r.url.isBlank()) return "Missing url"
        hide()

        val dm = ctx.resources.displayMetrics
        val density = dm.density
        val w = (dm.widthPixels * r.widthPercent.coerceIn(5, 100) / 100f).toInt()
        val h = w * 9 / 16
        val margin = (r.marginDp * density).toInt()

        // Solid black container -> nothing behind it can show through
        val container = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }

        val pv = PlayerView(ctx).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShutterBackgroundColor(Color.BLACK)
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(pv)

        if (r.title.isNotBlank()) {
            container.addView(TextView(ctx).apply {
                text = r.title
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setBackgroundColor(0xAA000000.toInt())
                setPadding((8 * density).toInt(), (4 * density).toInt(), (8 * density).toInt(), (4 * density).toInt())
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                )
            })
        }

        val err = TextView(ctx).apply {
            text = "Connecting…"
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(err)
        errorView = err

        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = when (r.position.lowercase()) {
                "top_left" -> Gravity.TOP or Gravity.START
                "bottom_left" -> Gravity.BOTTOM or Gravity.START
                "bottom_right" -> Gravity.BOTTOM or Gravity.END
                "center" -> Gravity.CENTER
                else -> Gravity.TOP or Gravity.END
            }
            x = margin
            y = margin
        }

        try {
            wm.addView(container, lp)
        } catch (e: Exception) {
            return "Could not add overlay: ${e.message}"
        }
        root = container
        currentUrl = r.url

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 5000, 500, 1000)
            .build()
        val p = ExoPlayer.Builder(ctx).setLoadControl(loadControl).build()
        p.volume = if (r.mute) 0f else 1f
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                err.visibility = if (state == Player.STATE_READY) View.GONE else View.VISIBLE
                if (state == Player.STATE_READY) err.text = "Connecting…"
            }

            override fun onPlayerError(error: PlaybackException) {
                err.text = "Stream error – retrying…"
                err.visibility = View.VISIBLE
                handler.postDelayed({ if (player === p) { loadMedia(p, r.url); p.prepare() } }, 3000)
            }
        })
        pv.player = p
        player = p
        loadMedia(p, r.url)
        p.prepare()

        if (r.timeoutSec > 0) {
            hideRunnable = Runnable { hide() }.also { handler.postDelayed(it, r.timeoutSec * 1000L) }
        }
        return null
    }

    private fun loadMedia(p: ExoPlayer, url: String) {
        val item = MediaItem.fromUri(Uri.parse(url))
        if (url.startsWith("rtsp", ignoreCase = true)) {
            // TCP is far more reliable than UDP on home networks
            p.setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(item))
        } else {
            p.setMediaItem(item)
        }
    }

    fun hide() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        errorView = null
        currentUrl = null
    }
}

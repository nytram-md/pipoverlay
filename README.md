# PIP Overlay (Android TV / Google TV)

Small always-on service that exposes a local HTTP API (port 5002). Home Assistant calls it
to show a picture-in-picture video window over whatever is on screen. The window is drawn on a
solid black opaque surface, so nothing shows through the video.

## Build
- Push this folder to a GitHub repo -> Actions -> "Build APK" -> download artifact `app-debug.apk`
- Or open in Android Studio and run Build > Build APK(s)

## Install
adb connect <TV_IP>:5555
adb install app-debug.apk
adb shell appops set com.martyn.pipoverlay SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.martyn.pipoverlay android.permission.POST_NOTIFICATIONS
Then launch "PIP Overlay" once from the TV apps row.

## API
POST /pip/show  {"url": "...", "position": "top_right|top_left|bottom_right|bottom_left|center",
                 "width_percent": 25, "margin": 24, "timeout": 30, "title": "Front door", "mute": true}
POST /pip/hide
GET  /status

Supports RTSP (forced TCP), HLS (.m3u8), DASH, MP4. Not MJPEG - use go2rtc to expose an RTSP/HLS stream.

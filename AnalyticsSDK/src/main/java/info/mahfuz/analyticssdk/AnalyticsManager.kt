package info.mahfuz.analyticssdk

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.IOException
import java.util.Locale
import java.util.Timer
import java.util.TimerTask


class AnalyticsManager(
    private val context: Context,
    private val player: ExoPlayer,
    private val userId: String,
    private val videoId: String,
    private val title: String,
    private val playerView: View
) {
    private val events: MutableList<PlayerEvent> = ArrayList()
    private val handler = Handler(Looper.getMainLooper())
    private val publishTimer = Timer()
    private var videoHeight: Int = 0;
    private var videoWidth: Int = 0;


    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) onPause()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onVideoEnd()
            }

            override fun onTracksChanged(tracks: Tracks) {
                collectTrackData(tracks)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                setResolution(videoSize)
            }

            override fun onPlayerError(error: PlaybackException) {
                setError(error)
            }
        })

        startCollecting()
        schedulePublishing()
    }

    private fun startCollecting() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                collectEventData()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun getResolutionLabel(height: Int): String {
        return when {
            height >= 2160 -> "4K"
            height >= 1440 -> "2K"
            height >= 1080 -> "1080p (Full HD)"
            height >= 720 -> "720p (HD)"
            height >= 480 -> "480p (SD)"
            height >= 360 -> "360p"
            height >= 240 -> "240p"
            height == 0 -> "UNKNOWN"
            else -> "Low"
        }
    }

    private fun setResolution(videoSize: VideoSize) {
        videoWidth = videoSize.width
        videoHeight = videoSize.height
    }


    private fun collectEventData() {
        val event = getEvent()

        events.add(event)
    }

    private fun getEvent(): PlayerEvent {
        val event = PlayerEvent(userId, videoId, title)

        event.isPlayingAd = player.isPlayingAd
        event.playbackPosition = player.currentPosition
        event.playbackSpeed = player.playbackParameters.speed
        event.isPlaying = player.isPlaying
        event.isBuffering = player.isLoading
        event.bufferedPercentage = player.bufferedPercentage
        event.volume = player.volume
        event.osVersion = Build.VERSION.RELEASE
        event.networkType = networkType
        event.networkSpeed = networkSpeed
        event.connectionType = connectionType
        val location = location
        if (location != null) {
            event.latitude = location.latitude
            event.longitude = location.longitude

            getLocationInfo(event)
        }
        event.isMuted = player.isDeviceMuted
        event.isFullscreen = isFullscreen
        event.videoWidth = videoWidth
        event.videoHeight = videoHeight
        event.resolution = resolution

        return event
    }

    private fun setError(error: PlaybackException) {
        val event = getEvent()
        event.errorMessage = error.message
        event.errorCode = error.errorCode
        events.add(event)
    }

    private fun getLocationInfo(ev: PlayerEvent) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(ev.latitude, ev.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                ev.city = addresses[0].locality
                ev.country = addresses[0].countryName
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @OptIn(UnstableApi::class) private fun collectTrackData(tracks: Tracks) {
        for (trackGroup in tracks.groups) {
            for (i in 0 until trackGroup.length) {
                val ev = getEvent()
                val format: Format = trackGroup.getTrackFormat(i)
                if (MimeTypes.isVideo(format.sampleMimeType)) {
                    ev.bitrate = format.bitrate
                    ev.framerate = format.bitrate
                    ev.videoCodec = format.codecs
                } else if (MimeTypes.isAudio(format.sampleMimeType)) {
                    ev.audioCodec = format.codecs
                }
                events.add(ev)
            }
        }
    }


    private fun schedulePublishing() {
        publishTimer.schedule(object : TimerTask() {
            override fun run() {
                publishEvents()
            }
        }, 10000, 10000)
    }

    private fun publishEvents() {
        if (events.isEmpty()) return

        val endpointUrl = "https://your.analytics.endpoint.com/events"
        try {
            HttpClient.post(endpointUrl, events)
            events.clear()
        } catch (e: Exception) {
            System.err.println("Failed to publish events: " + e.message)
        }
    }

    fun onPause() {
        publishEvents()
    }

    fun onVideoEnd() {
        publishEvents()
    }

    fun onClose() {
        publishEvents()
    }

    private val resolution: String
        get() {
            return getResolutionLabel(videoHeight)
        }

    private val networkSpeed: Double
        @SuppressLint("MissingPermission")
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)

            if (capabilities != null) {
                return capabilities.linkDownstreamBandwidthKbps / 1000.0
            }
            return 0.0
        }

    private val isFullscreen: Boolean
        get() {
            val displayMetrics: DisplayMetrics = context.resources.displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val screenWidth = displayMetrics.widthPixels
            return playerView.height == screenHeight && playerView.width == screenWidth
        }

    private val connectionType: String
        @SuppressLint("MissingPermission")
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)

            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return "WiFi"
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return "Mobile"
                }
            }
            return "Unknown"
        }

    private val networkType: String
        @SuppressLint("MissingPermission")
        get() {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val networkType = tm.networkType
            return when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Unknown"
            }
        }

    private val location: Location?
        @SuppressLint("MissingPermission")
        get() {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                return locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                System.err.println("Location access not permitted: " + e.message)
            }
            return null
        }
}

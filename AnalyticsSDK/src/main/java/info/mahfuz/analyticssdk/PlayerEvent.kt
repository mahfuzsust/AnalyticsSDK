package info.mahfuz.analyticssdk

import java.util.Date
import java.util.UUID


data class PlayerEvent(
    private val userId: String,
    private val videoId: String,
    private val title: String
) {
     val timestamp = Date()
     val id = UUID.randomUUID()
     var playbackPosition: Long = 0
     var playbackSpeed = 0f
     var isPlaying = false
     var isBuffering = false
     var isFullscreen = false
     var isPlayingAd = false
     var bufferedPercentage = 0
     var videoWidth: Int = 0
     var videoHeight: Int = 0
     var resolution: String? = null
     var bitrate = 0
     var framerate = 0
     var videoCodec: String? = null
     var audioCodec: String? = null
     var volume = 0f
     var isMuted = false
     var errorCode: Int? = null
     var errorMessage: String? = null
     var osVersion: String? = null
     var networkType: String? = null
     var networkSpeed = 0.0
     var connectionType: String? = null
     var latitude = 0.0
     var longitude = 0.0
     var city: String? = null
     var country: String? = null
}

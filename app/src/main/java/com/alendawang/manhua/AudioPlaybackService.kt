package com.alendawang.manhua

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.alendawang.manhua.model.AudioHistory
import com.alendawang.manhua.model.AudioTrack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class AudioPlaybackSnapshot(
    val audioId: String? = null,
    val trackIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val showLyrics: Boolean = false,  // 记住用户上次的歌词显示状态
    val currentTrackUri: String? = null  // 当前曲目URI，供悬浮歌词使用
)

object AudioPlaybackBus {
    val state = MutableStateFlow(AudioPlaybackSnapshot())
}

class AudioPlaybackService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var updateJob: Job? = null
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var currentSpeed: Float = 1.0f

    private var audioId: String? = null
    private var audioName: String? = null
    private var coverUriString: String? = null
    private var trackNames: List<String> = emptyList()
    private var trackUris: List<String> = emptyList()
    private var trackIndex: Int = 0
    private var autoPlay: Boolean = false
    private var playlist: List<AudioHistory> = emptyList()
    private var playlistIndex: Int = 0

    private lateinit var mediaSession: MediaSessionCompat

    override fun onCreate() {

        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioPlayback")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = play()
            override fun onPause() = pause()
            override fun onSkipToNext() = skipToNext()
            override fun onSkipToPrevious() = skipToPrevious()
            override fun onSeekTo(pos: Long) = seekTo(pos)
        })
        mediaSession.isActive = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val newAudioId = intent.getStringExtra(EXTRA_AUDIO_ID)
                val newTrackIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                val newStartPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
                autoPlay = intent.getBooleanExtra(EXTRA_AUTO_PLAY, true)
                val playlistJson = intent.getStringExtra(EXTRA_PLAYLIST_JSON)
                playlist = parsePlaylist(playlistJson)

                val sameAudio = audioId == newAudioId
                val newUris = intent.getStringArrayListExtra(EXTRA_TRACK_URIS)
                val newNames = intent.getStringArrayListExtra(EXTRA_TRACK_NAMES)
                val samePlaylist = newUris != null && trackUris == newUris
                val sameIndex = trackIndex == newTrackIndex

                if (sameAudio && samePlaylist && sameIndex && player != null) {
                    // 当 startPosition > 0 时才 seek（startPosition = -1 表示保持当前位置）
                    if (newStartPosition > 0) {
                        seekTo(newStartPosition)
                    }
                    if (autoPlay && player?.isPlaying == false) {
                        play()
                    }
                    updateNotification()
                    return START_STICKY
                }

                audioId = newAudioId
                audioName = intent.getStringExtra(EXTRA_AUDIO_NAME)
                coverUriString = intent.getStringExtra(EXTRA_COVER_URI)
                trackNames = newNames ?: emptyList()
                trackUris = newUris ?: emptyList()
                trackIndex = newTrackIndex.coerceIn(0, maxOf(0, trackUris.size - 1))
                playlistIndex = playlist.indexOfFirst { it.id == newAudioId }.takeIf { it >= 0 } ?: 0

                cachePlaybackSession()
                prepareTrack(newStartPosition)
            }
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> skipToNext()
            ACTION_PREV -> skipToPrevious()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POSITION, 0L)
                seekTo(pos)
            }
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setPlaybackSpeed(speed)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        updateJob?.cancel()
        player?.release()
        player = null
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun prepareTrack(startPosition: Long) {
        val uriString = trackUris.getOrNull(trackIndex) ?: return
        val uri = Uri.parse(uriString)
        player?.release()
        player = MediaPlayer()
        isPrepared = false
        player?.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        player?.setOnPreparedListener {
            isPrepared = true
            if (startPosition > 0) {
                it.seekTo(startPosition.toInt())
            }
            updateMediaMetadata(it.duration.toLong())
            updateSnapshot()
            if (autoPlay) {
                it.start()
                updateSnapshot(isPlayingOverride = true)
                startProgressUpdates()
            }
            updateNotification()
        }
        player?.setOnCompletionListener {
            if (trackIndex < trackUris.size - 1) {
                skipTo(trackIndex + 1)
            } else {
                pause()
            }
        }
        try {
            player?.setDataSource(this, uri)
            player?.prepareAsync()
        } catch (_: Exception) {
        }
        updateNotification()
    }

    private fun startProgressUpdates() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (player != null && player?.isPlaying == true) {
                updateSnapshot()
                delay(500)
            }
        }
    }

    private fun updateSnapshot(isPlayingOverride: Boolean? = null) {
        val duration = player?.duration?.toLong() ?: 0L
        val position = player?.currentPosition?.toLong() ?: 0L
        val isPlaying = isPlayingOverride ?: (player?.isPlaying == true)
        updatePlaybackState(isPlaying, position)
        updateCachedTrackIndex()
        AudioPlaybackBus.state.update {
            it.copy(
                audioId = audioId,
                trackIndex = trackIndex,
                isPlaying = isPlaying,
                positionMs = position,
                durationMs = duration,
                currentTrackUri = trackUris.getOrNull(trackIndex)
            )
        }
    }

    private fun play() {
        restoreCachedSessionIfNeeded()
        if (!isPrepared) {
            prepareTrack(0L)
            autoPlay = true
            return
        }
        player?.start()
        applyPlaybackSpeed()
        updateSnapshot(isPlayingOverride = true)
        startProgressUpdates()
        updateNotification()
    }

    private fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.25f, 3.0f)
        applyPlaybackSpeed()
    }

    private fun applyPlaybackSpeed() {
        if (isPrepared && player != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = player!!.playbackParams
                params.speed = currentSpeed
                player!!.playbackParams = params
            } catch (_: Exception) {
                // Some devices may not support speed change
            }
        }
    }

    private fun pause() {
        restoreCachedSessionIfNeeded()
        player?.pause()
        updateSnapshot(isPlayingOverride = false)
        updateNotification()
    }

    private fun toggle() {
        restoreCachedSessionIfNeeded()
        if (player?.isPlaying == true) pause() else play()
    }

    private fun seekTo(positionMs: Long) {
        restoreCachedSessionIfNeeded()
        if (isPrepared) {
            player?.seekTo(positionMs.toInt())
            updateSnapshot()
            updateNotification()
        }
    }

    private fun skipTo(newIndex: Int) {
        restoreCachedSessionIfNeeded()
        if (trackUris.isEmpty()) return
        trackIndex = newIndex.coerceIn(0, trackUris.size - 1)
        autoPlay = true
        updateCachedTrackIndex()
        prepareTrack(0L)
    }

    private fun updateNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val isPlaying = player?.isPlaying == true
        val title = trackNames.getOrNull(trackIndex) ?: "Unknown Track"
        val content = audioName ?: "Audio"
        val cover = loadCoverBitmap()

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                servicePendingIntent(ACTION_PAUSE, 2)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                servicePendingIntent(ACTION_PLAY, 2)
            )
        }

        // ContentIntent: clicking notification opens audio player
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO_AUDIO_PLAYER, true)
            putExtra(EXTRA_AUDIO_ID, audioId)
            putExtra(EXTRA_START_INDEX, trackIndex)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this, 100, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setLargeIcon(cover)
            .setContentIntent(contentPendingIntent)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Prev",
                    servicePendingIntent(ACTION_PREV, 1)
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    servicePendingIntent(ACTION_NEXT, 3)
                )
            )
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .build()
    }

    private fun updateMediaMetadata(durationMs: Long) {
        val title = trackNames.getOrNull(trackIndex) ?: "Unknown Track"
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, audioName ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState(isPlaying: Boolean, positionMs: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun skipToNext() {
        if (playlist.isNotEmpty()) {
            val nextIndex = (playlistIndex + 1).coerceAtMost(playlist.lastIndex)
            if (nextIndex != playlistIndex) {
                switchToPlaylistIndex(nextIndex)
                return
            }
        }
        skipTo(trackIndex + 1)
    }

    private fun skipToPrevious() {
        if (playlist.isNotEmpty()) {
            val prevIndex = (playlistIndex - 1).coerceAtLeast(0)
            if (prevIndex != playlistIndex) {
                switchToPlaylistIndex(prevIndex)
                return
            }
        }
        skipTo(trackIndex - 1)
    }

    private fun switchToPlaylistIndex(newIndex: Int) {
        val target = playlist.getOrNull(newIndex) ?: return
        playlistIndex = newIndex
        audioId = target.id
        audioName = target.name
        coverUriString = target.coverUriString
        trackNames = target.tracks.map { it.name }
        trackUris = target.tracks.map { it.uriString }
        trackIndex = 0
        autoPlay = true
        cachePlaybackSession()
        prepareTrack(0L)
    }

    private fun parsePlaylist(json: String?): List<AudioHistory> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AudioHistory>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun cachePlaybackSession() {
        lastPayload = PlaybackPayload(
            audioId = audioId,
            audioName = audioName,
            coverUriString = coverUriString,
            trackUris = trackUris,
            trackNames = trackNames,
            trackIndex = trackIndex
        )
    }

    private fun updateCachedTrackIndex() {
        val payload = lastPayload ?: return
        lastPayload = payload.copy(trackIndex = trackIndex)
    }

    private fun restoreCachedSessionIfNeeded() {
        if (trackUris.isNotEmpty() && audioId != null) return
        val payload = lastPayload ?: return
        audioId = payload.audioId
        audioName = payload.audioName
        coverUriString = payload.coverUriString
        trackUris = payload.trackUris
        trackNames = payload.trackNames
        trackIndex = payload.trackIndex.coerceIn(0, maxOf(0, trackUris.size - 1))
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AudioPlaybackService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun loadCoverBitmap(): Bitmap? {
        val cover = coverUriString
        val bitmap = if (cover != null) {
            try {
                // 判断是文件路径还是 URI
                if (cover.startsWith("/")) {
                    // 绝对文件路径
                    val file = java.io.File(cover)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(cover)
                    } else null
                } else {
                    // URI 格式
                    contentResolver.openInputStream(Uri.parse(cover))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        } else null
        
        // 如果无封面，使用默认封面
        return bitmap ?: BitmapFactory.decodeResource(resources, R.drawable.default_audio_cover)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "audio_playback"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.alendawang.manhua.action.START"
        private const val ACTION_PLAY = "com.alendawang.manhua.action.PLAY"
        private const val ACTION_PAUSE = "com.alendawang.manhua.action.PAUSE"
        private const val ACTION_TOGGLE = "com.alendawang.manhua.action.TOGGLE"
        private const val ACTION_NEXT = "com.alendawang.manhua.action.NEXT"
        private const val ACTION_PREV = "com.alendawang.manhua.action.PREV"
        private const val ACTION_SEEK = "com.alendawang.manhua.action.SEEK"
        private const val ACTION_SET_SPEED = "com.alendawang.manhua.action.SET_SPEED"
        private const val ACTION_STOP = "com.alendawang.manhua.action.STOP"

        private const val EXTRA_AUDIO_ID = "extra_audio_id"
        private const val EXTRA_AUDIO_NAME = "extra_audio_name"
        private const val EXTRA_COVER_URI = "extra_cover_uri"
        private const val EXTRA_TRACK_URIS = "extra_track_uris"
        private const val EXTRA_TRACK_NAMES = "extra_track_names"
        private const val EXTRA_START_INDEX = "extra_start_index"
        private const val EXTRA_START_POSITION = "extra_start_position"
        private const val EXTRA_AUTO_PLAY = "extra_auto_play"
        private const val EXTRA_SEEK_POSITION = "extra_seek_position"
        private const val EXTRA_SPEED = "extra_speed"
        private const val EXTRA_PLAYLIST_JSON = "extra_playlist_json"
        const val EXTRA_NAVIGATE_TO_AUDIO_PLAYER = "extra_navigate_to_audio_player"

        private data class PlaybackPayload(
            val audioId: String?,
            val audioName: String?,
            val coverUriString: String?,
            val trackUris: List<String>,
            val trackNames: List<String>,
            val trackIndex: Int
        )

        @Volatile
        private var lastPayload: PlaybackPayload? = null

        fun startPlayback(
            context: Context,
            audio: AudioHistory,
            startIndex: Int,
            startPosition: Long,
            autoPlay: Boolean,
            playlist: List<AudioHistory>
        ) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_AUDIO_ID, audio.id)
                putExtra(EXTRA_AUDIO_NAME, audio.name)
                putExtra(EXTRA_COVER_URI, audio.coverUriString)
                putStringArrayListExtra(
                    EXTRA_TRACK_URIS,
                    ArrayList(audio.tracks.map { it.uriString })
                )
                putStringArrayListExtra(
                    EXTRA_TRACK_NAMES,
                    ArrayList(audio.tracks.map { it.name })
                )
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_START_POSITION, startPosition)
                putExtra(EXTRA_AUTO_PLAY, autoPlay)
                putExtra(EXTRA_PLAYLIST_JSON, Gson().toJson(playlist))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        private fun sendAction(context: Context, action: String) {
            val intent = Intent(context, AudioPlaybackService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }

        fun seekTo(context: Context, positionMs: Long) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_POSITION, positionMs)
            }
            ContextCompat.startForegroundService(context, intent)
        }



        fun toggle(context: Context) = sendAction(context, ACTION_TOGGLE)
        fun next(context: Context) = sendAction(context, ACTION_NEXT)
        fun prev(context: Context) = sendAction(context, ACTION_PREV)

        fun setSpeed(context: Context, speed: Float) {
            val intent = Intent(context, AudioPlaybackService::class.java).apply {
                action = ACTION_SET_SPEED
                putExtra(EXTRA_SPEED, speed)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

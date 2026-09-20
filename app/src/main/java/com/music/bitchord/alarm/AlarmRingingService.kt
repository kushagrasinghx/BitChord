package com.music.bitchord.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.music.bitchord.data.Http
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.*

/** One short-lived alarm player. It never owns or edits BitChord's ordinary media queue. */
class AlarmRingingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var resolveJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var alarmId: String? = null
    private var token: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START) startRinging(intent)
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val occurrence = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val alarm = AlarmStore.current(this).alarms.firstOrNull { it.id == id } ?: return
        intent.getIntExtra(EXTRA_REPLACED_PREVIOUS, -1).takeIf { it >= 0 }
            ?.let { AlarmVolumeController.restore(this, it) }
        releasePlayer()
        alarmId = id
        token = occurrence
        startForeground(AlarmNotification.ID, AlarmNotification.active(this, alarm, occurrence))
        pauseOrdinaryBitChordPlayback()
        AlarmVolumeController.apply(this, alarm, occurrence)
        requestAlarmFocus()
        resolveJob = scope.launch {
            runCatching { AlarmStreamResolver.resolve(requireNotNull(alarm.song)) }
                .onSuccess(::startResolvedStream)
                .onFailure { finishSession(failed = true) }
        }
    }

    private fun startResolvedStream(stream: AlarmResolvedStream) {
        val dataSource = OkHttpDataSource.Factory(Http.client).setDefaultRequestProperties(stream.headers)
        val ringingPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(ALARM_ATTRIBUTES, false)
            .build()
        player = ringingPlayer
        ringingPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) finishSession()
            }
            override fun onPlayerError(error: PlaybackException) {
                finishSession(failed = true)
            }
        })
        ringingPlayer.setMediaItem(MediaItem.fromUri(stream.url))
        ringingPlayer.repeatMode = Player.REPEAT_MODE_OFF
        ringingPlayer.prepare()
        ringingPlayer.play()
    }

    private fun pauseOrdinaryBitChordPlayback() {
        val session = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, session).buildAsync()
        future.addListener({
            runCatching { future.get() }.getOrNull()?.pause()
            MediaController.releaseFuture(future)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestAlarmFocus() {
        val audio = getSystemService(AudioManager::class.java)
        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
            .also(audio::requestAudioFocus)
    }

    private fun finishSession(failed: Boolean = false) {
        val id = alarmId ?: return
        val occurrence = token ?: return
        val ended = AlarmScheduler.stop(this, id, occurrence)
        releaseAndStop(ended?.previousAlarmVolume)
        if (failed) AlarmNotification.showFailure(this)
    }

    private fun releasePlayer() {
        resolveJob?.cancel()
        resolveJob = null
        player?.stop()
        player?.release()
        player = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releaseAndStop(previousVolume: Int?) {
        releasePlayer()
        AlarmVolumeController.restore(this, previousVolume)
        AlarmNotification.cancel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        val ended = alarmId?.let { id -> token?.let { AlarmScheduler.stop(this, id, it) } }
        releasePlayer()
        AlarmVolumeController.restore(this, ended?.previousAlarmVolume)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "alarm.ringing.START"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TOKEN = "token"
        private const val EXTRA_REPLACED_PREVIOUS = "replaced_previous"
        val ALARM_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        fun start(context: Context, request: AlarmScheduler.TriggerRequest) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmRingingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ID, request.alarm.id)
                    .putExtra(EXTRA_TOKEN, request.token)
                    .putExtra(EXTRA_REPLACED_PREVIOUS, request.replaced?.previousAlarmVolume ?: -1),
            )
        }

        fun stop(context: Context, previousVolume: Int?) {
            AlarmVolumeController.restore(context, previousVolume)
            AlarmNotification.cancel(context)
            context.stopService(Intent(context, AlarmRingingService::class.java))
        }
    }
}

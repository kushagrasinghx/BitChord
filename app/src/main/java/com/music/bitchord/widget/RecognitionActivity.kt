package com.music.bitchord.widget

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.shazam.RecognitionSession
import com.music.bitchord.data.shazam.RecognitionSessionState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The recognition widget and Quick Settings tile's microphone, in window form.
 *
 * The widget opens this instead of MainActivity and keeps the window invisible;
 * the tile opens the same activity with a visible recognition surface. Both
 * entry points share the same microphone and result flow:
 *
 * 1. The mic permission is asked only when it is missing; a granted one goes
 *    straight past to listening, and a refusal ends the request where it
 *    stands — the pill goes back to sleep and nothing opens, because the
 *    user just said no and the in-app mic button remains for later.
 * 2. Listening is [RecognitionSession] itself — the same staged
 *    2 → 4 → 8 → 12 second take the in-app sheet runs, with every look-up
 *    sharing the one accumulating clip. Its [RecognitionSession.level] is
 *    what the pill's bars are drawn from: the same five-bar waveform the
 *    sheet draws, phase-breathing on idle and rising with the room, shipped
 *    to the launcher as frame bitmaps — [showWave].
 * 3. A find hands `"Title - Artist"` to [RecognitionDeepLink] and then opens
 *    [MainActivity]: the app comes up on Search, matches the song to a track
 *    and starts playing it with the player card over everything.
 * 4. Nothing found goes back to sleep: a toast from wherever the user is,
 *    and the window closes without ever having shown itself.
 *
 * While a take is live the window — every touch of it — is the stop button.
 * The second tap on the widget lands on this glass first, and stops there;
 * a tap anywhere else stops just the same, like any tap-to-cancel overlay.
 * Nothing below the glass can be reached while a take runs, and nothing has
 * to be: there is no second meaning a touch could have.
 *
 * Being backgrounded takes the microphone with it on Android's own rules, so
 * [onStop] mid-listen is treated as the user abandoning the request rather
 * than as something to finish in the background.
 */
class RecognitionActivity : Activity() {

    private var showInterface = false
    private var overlayView: RecognitionOverlayView? = null
    private var session: RecognitionSession? = null

    /**
     * The scope that runs the frame watch, kept so [abandon] can cancel the
     * whole thing at once. Alone it is a leak — cancelling it is the only way
     * a leaving take stops.
     */
    private var scope: CoroutineScope? = null
    private var listening = false

    /**
     * The one bitmap every listening frame is drawn into. Reused across the
     * take so the render loop never allocates — a fresh bitmap per frame at
     * the taking cadence GC-pauses itself into the stutter the partial
     * updates just removed.
     */
    private var frameBitmap: Bitmap? = null

    /**
     * The app's own playback, borrowed for the take: bound only when a song
     * is audible, paused so the microphone hears the room and never the echo
     * of itself, resumed when the take ends without a match — by the stop
     * hand, by backgrounding, by a no-match — and left paused when a fresh
     * song arrives to take the room instead.
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playbackController: MediaController? = null
    private var pausedForListen = false

    /**
     * The glass itself is the stop button: while a take is live, any touch
     * on the window ends it — the second tap on the widget plainly lands
     * here first, because this window sits over the launcher.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (listening) {
            abandon()
            finish()
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showInterface = intent?.getBooleanExtra(EXTRA_SHOW_INTERFACE, false) == true
        installContentView()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startListeningWhenReady()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_MICROPHONE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListeningWhenReady()
                } else {
                    // A refusal stops here rather than route somewhere that will ask
                    // again — the answer was taken, not lost.
                    RecognitionWidgetProvider.showSleep(this)
                    finish()
                }
            }
            REQUEST_NOTIFICATIONS -> startListening()
        }
    }

    /** Notification mode needs the Android 13+ grant before its result can surface. */
    private fun startListeningWhenReady() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AppSettings.autoPlayRecognizedSong.value &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            startListening()
        }
    }

    // A second tap while a take is live is the stop button: the pill goes
    // back to sleep and the microphone lets go. A tap into a live window
    // whose take is already over — abandoned from the background, or already
    // settling its ending — restarts the listen, exactly what a first tap
    // would have done on a clean launcher.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SHOW_INTERFACE, false) && !showInterface) {
            showInterface = true
            installContentView()
        }
        if (listening) {
            abandon()
            finish()
        } else {
            startListeningWhenReady()
        }
    }

    /**
     * Runs the one take: the session drives itself through every progressive
     * search and its own recorder cleanup — recording on IO, fingerprinting
     * and lookups on workers, none of it here — while the frame watch that
     * drives the widget rides this same thread. The loop steps the sheet's
     * waveform on real time — 2π over 1.4 seconds, the sheet's own period —
     * and the frames convene with the music: tight while a beat is in the
     * room, easy in the silences between. Falling out of the loop is the
     * same moment the session reaches a terminal state.
     */
    private fun startListening() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        this.scope = scope
        val session = RecognitionSession(scope)
        this.session = session
        listening = true
        val density = applicationContext.resources.displayMetrics.density
        scope.launch {
            // The room may already be carrying this app's own song, and the
            // microphone would hear its echo before anything else: pause
            // playback for the take so the recognition hears the world first.
            pauseOursIfPlaying()
            session.start()
            overlayView?.setSearching(session.state.value == RecognitionSessionState.Searching)
            var phase = 0f
            var lastFrame = SystemClock.elapsedRealtime()
            var state = session.state.value
            while (
                state == RecognitionSessionState.Listening ||
                state == RecognitionSessionState.Searching
            ) {
                val level = session.level.value
                delay(if (level >= BEAT_LEVEL) BEAT_FRAME_MS else IDLE_FRAME_MS)
                val now = SystemClock.elapsedRealtime()
                phase = (phase + (now - lastFrame) * PHASE_PER_MS) % (2 * PI).toFloat()
                lastFrame = now
                val frame = nextFrame(density, phase, level)
                RecognitionWidgetProvider.showWave(applicationContext, frame)
                overlayView?.setAudioLevel(level)
                state = session.state.value
                overlayView?.setSearching(state == RecognitionSessionState.Searching)
            }
            listening = false
            when (val settled = state) {
                is RecognitionSessionState.Found -> {
                    if (AppSettings.autoPlayRecognizedSong.value) {
                        // The match is posted before the launch, not during it:
                        // the composition reads the flow the moment it builds,
                        // and the stream is what carries the query whether
                        // MainActivity comes up cold or arrives warm.
                        RecognitionDeepLink.postMatch(settled.track.searchQuery)
                        RecognitionWidgetProvider.showSleep(applicationContext)
                        startActivity(
                            Intent(applicationContext, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        )
                        // The app arrives on a fade rather than the launcher's
                        // window-slam: the pill just finished breathing on the
                        // home screen, so the transition into playing the song
                        // is the two joining, not one replacing the other.
                        overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out,
                        )
                        finish()
                    } else {
                        // Notification mode leaves the room's existing song in
                        // the same state it had before recognition started.
                        RecognitionNotifications.post(applicationContext, settled.track)
                        resumePlayback()
                        RecognitionWidgetProvider.showSleep(applicationContext)
                        finish()
                    }
                }

                else -> {
                    // No song came out of the take: the room's playback goes
                    // back to where the tap left it, and only then the
                    // window's goodbye.
                    resumePlayback()
                    Toast.makeText(applicationContext, R.string.widget_recognition_no_match, Toast.LENGTH_SHORT)
                        .show()
                    RecognitionWidgetProvider.showSleep(applicationContext)
                    finish()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (listening) abandon()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (listening) abandon()
        // A match path may have left its borrowed controller behind: the
        // player card's own connection takes it from here, ours lets go.
        releaseControllerQuietly()
        overlayView = null
    }

    /**
     * Leaves the take without an ending: the watch is cancelled, the pill
     * goes back to sleep — right now, in this same turn of the looper — and
     * the microphone lets go. Idempotent by way of the flag, so the settled
     * paths — which unset [listening] first and close the window after —
     * falling through [onStop] on the way out touch none of this.
     *
     * The sleep can't be raced here, and that is the point of frames riding
     * the main thread: a cancelled watch's next frame is never given a turn,
     * and no frame of the last one exists anymore by the time this runs. The
     * sleep face is therefore the last picture the widget is handed — a stop
     * always reads as sleep, the state the second tap promised.
     */
    private fun abandon() {
        listening = false
        scope?.cancel()
        RecognitionWidgetProvider.showSleep(this)
        session?.stop()
        // The stop hand is an ending without a song: whatever the take
        // paused for the mic gets rewound to playing.
        resumePlayback()
        session = null
        scope = null
    }

    private fun installContentView() {
        overlayView = if (showInterface) RecognitionOverlayView(this) else null
        // The invisible widget path deliberately keeps a paint-less view so
        // the launcher remains untouched. The tile path swaps in the visible
        // recognition surface while sharing this same activity and session.
        setContentView(overlayView ?: View(this))
    }

    /**
     * Pauses the app's own playback for the take, if a song of ours is
     * audible. The check runs over the audio state, not over a connection,
     * so a silent room means no session is ever bound and a cold tap from
     * the launcher costs nothing. The controller is kept only while it
     * holds a paused song for [resumePlayback]; another player's song on
     * the phone — or ours already resting — is left exactly as found.
     */
    private suspend fun pauseOursIfPlaying() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (!audioManager.isMusicActive) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        val controller = try {
            withTimeoutOrNull(PAUSE_CONNECT_MS) { future.await() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            null
        }
        if (controller != null && controller.isConnected && controller.isPlaying) {
            playbackController = controller
            controller.pause()
            pausedForListen = true
        } else {
            releaseControllerQuietly()
        }
    }

    /**
     * Hands the room's song back on the endings that aren't a fresh match:
     * the stop hand, backgrounding, a no-match, a failure. Guarded by the
     * paused flag, so every path can call it blind; play goes out before
     * the release, because a released controller answers nothing.
     */
    private fun resumePlayback() {
        if (!pausedForListen) return
        pausedForListen = false
        runCatching { playbackController?.play() }
        releaseControllerQuietly()
    }

    /**
     * Lets go of the borrowed controller. Never while it owes a resume —
     * [resumePlayback] plays first — and perfectly safe to call twice: both
     * bonds are null by the end of the first call.
     */
    private fun releaseControllerQuietly() {
        controllerFuture?.cancel(true)
        controllerFuture = null
        runCatching { playbackController?.release() }
        playbackController = null
    }

    /**
     * One frame of the sheet's [LiveWaveform], drawn white on the black
     * capsule exactly as the sheet wears its own bars. The arithmetic is the
     * composable's, bar for bar: an idle height that breathes on a per-bar
     * phase, scaled up by the current level. The sleeping face is this
     * drawing at phase zero frozen into [ic_widget_wave_sleep], so the very
     * first frame the widget shows is a continuation of its own sleep.
     *
     * The frame is reused across the whole take — only the beat in it
     * changes from one to the next — because allocating a fresh bitmap per
     * frame at this cadence is a GC pause away from the stutter the partial
     * updates just removed.
     */
    private fun nextFrame(density: Float, phase: Float, level: Float): Bitmap {
        val width = (WAVE_WIDTH_DP * density).roundToInt()
        val height = (WAVE_HEIGHT_DP * density).roundToInt()
        val bitmap = frameBitmap
            ?.takeIf { it.width == width && it.height == height && it.isMutable }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                .also { frameBitmap = it }
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        val gap = width * 0.10f
        val barWidth = (width - gap * 4f) / 5f
        val maxHeight = height * 0.94f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BAR_COLOR }
        val bar = RectF()
        repeat(5) { index ->
            val wave = 0.30f + 0.70f * abs(sin(phase + index * 0.85f))
            val idleHeight = height * (0.22f + 0.12f * wave)
            val barHeight = idleHeight + (maxHeight - idleHeight) * level * wave
            val left = index * (barWidth + gap)
            bar.set(left, (height - barHeight) / 2f, left + barWidth, (height + barHeight) / 2f)
            paint.alpha = ((0.62f + level * 0.38f) * 0xFF + 0.5f).toInt()
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, paint)
        }
        return bitmap
    }

    companion object {
        const val EXTRA_SHOW_INTERFACE = "com.music.bitchord.widget.SHOW_INTERFACE"

        const val REQUEST_MICROPHONE = 41
        const val REQUEST_NOTIFICATIONS = 42

        /**
         * The frames' cadence in a room with music against a room resting.
         * Faster than the sheet's own cycle when the beat is coming through,
         * easy on the long silences a recognition listen is full of.
         */
        const val BEAT_FRAME_MS = 55L
        const val IDLE_FRAME_MS = 110L
        const val BEAT_LEVEL = 0.22f

        /** The waveform itself: the sheet runs its phase over 1.4 seconds. */
        const val PHASE_PER_MS = ((2 * PI).toFloat()) / 1400f

        /**
         * How long playback's session is given to connect for the pause.
         * Past it the take starts without the pause and the recognition
         * does its best over whatever is playing; the room's song keeps
         * going either way.
         */
        const val PAUSE_CONNECT_MS = 350L

        /** The ImageView the frames land in, from widget_recognition_wide.xml. */
        const val WAVE_WIDTH_DP = 88f
        const val WAVE_HEIGHT_DP = 54f

        /** The bars, white on the black capsule exactly as the sheet wears them. */
        const val BAR_COLOR = 0xFFFFFFFF.toInt()
    }
}

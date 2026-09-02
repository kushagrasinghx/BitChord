package com.music.bitchord.playback.smart

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Base64
import android.util.Log
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max

/**
 * Optional on-device ranking for the existing, already-safe Automix DJ plan.
 *
 * This does not create a transition or touch playback. It only turns a
 * low-confidence DJ blend into the planner's existing filtered handoff. A
 * missing, invalid, or disabled model is therefore indistinguishable from the
 * normal Automix path.
 */
object AutomixAiRanker {
    @Volatile private var appContext: Context? = null
    @Volatile private var session: OrtSession? = null
    private val lock = Any()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun refine(plan: TransitionPlan, outgoing: TrackAnalysis, incoming: TrackAnalysis): TransitionPlan {
        if (!AppSettings.automixAiEnabled.value || plan.transitionStyle != TransitionStyle.DJ_BLEND) return plan
        val score = score(plan, outgoing, incoming) ?: return plan
        if (score >= MIN_DJ_SCORE) return plan.copy(reason = "${plan.reason}-ai")
        return conservative(plan)
    }

    internal fun conservative(plan: TransitionPlan): TransitionPlan =
        plan.copy(
            transitionStyle = TransitionStyle.DJ_FILTER,
            bassSwap = false,
            filterSweep = max(plan.filterSweep, SAFE_FILTER_SWEEP),
            reason = "${plan.reason}-ai-conservative",
        )

    private fun score(plan: TransitionPlan, outgoing: TrackAnalysis, incoming: TrackAnalysis): Double? = runCatching {
        val active = session() ?: return null
        val outgoingBpm = outgoing.bpm.takeIf { it > 0 } ?: return null
        val incomingBpm = incoming.bpm.takeIf { it > 0 } ?: return null
        val features = floatArrayOf(
            (abs(outgoingBpm - incomingBpm) / max(max(outgoingBpm, incomingBpm), 1.0)).toFloat(),
            abs(plan.incomingPlaybackRate - 1.0).toFloat(),
            (plan.fadeSeconds / 32.0).coerceIn(0.0, 1.0).toFloat(),
            0f, // The model's training fade-difference feature is neutral for one planned overlap.
        )
        OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), FloatBuffer.wrap(features), longArrayOf(1, 4)).use { tensor ->
            active.run(mapOf(active.inputNames.first() to tensor)).use { outputs ->
                val probabilities = outputs.get(1).value as? List<*> ?: return null
                val row = probabilities.firstOrNull() as? Map<*, *> ?: return null
                (row[1L] as? Number)?.toDouble() ?: (row[1] as? Number)?.toDouble()
            }
        }
    }.onFailure { Log.w(TAG, "Automix AI unavailable; keeping standard DJ plan", it) }.getOrNull()

    private fun session(): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            val context = appContext ?: return null
            return runCatching {
                val model = File(context.filesDir, MODEL_FILE)
                if (!model.exists() || model.length() == 0L) {
                    val encoded = context.assets.open(MODEL_ASSET).bufferedReader().use { it.readText() }
                    model.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                }
                OrtEnvironment.getEnvironment().createSession(model.absolutePath, OrtSession.SessionOptions())
                    .also { session = it }
            }.onFailure { Log.w(TAG, "Automix AI model unavailable", it) }.getOrNull()
        }
    }

    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
            appContext = null
        }
    }

    private const val TAG = "BitChordAutomixAI"
    private const val MODEL_ASSET = "automix_electronic_ranker.onnx.b64"
    private const val MODEL_FILE = "automix_electronic_ranker.onnx"
    private const val MIN_DJ_SCORE = 0.60
    private const val SAFE_FILTER_SWEEP = 0.55
}

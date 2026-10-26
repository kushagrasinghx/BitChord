package com.music.bitchord.ui.performance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import com.music.bitchord.MainActivity

/** Native controls live outside the measured windows and do not run a Compose frame loop. */
class FrameDiagnosticsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var statusText: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var share: Button
    private lateinit var modes: Spinner
    private val refresh = object : Runnable {
        override fun run() {
            val status = FrameDiagnostics.status.value
            val remaining = ((status.stopAtUptimeMs - SystemClock.uptimeMillis() + 999) / 1_000).coerceAtLeast(0)
            statusText.text = if (status.recording) "Recording: ${remaining}s remaining, including the initial 3-second settling period." else status.message
            start.isEnabled = !status.recording && !status.writing
            stop.isEnabled = status.recording
            share.isEnabled = !status.recording && !status.writing
            modes.isEnabled = !status.recording && !status.writing
            main.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        if (!BuildConfig.PERFORMANCE_DIAGNOSTICS) { finish(); return }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(root) }
        val pad = (20 * resources.displayMetrics.density).toInt()
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            root.setPadding(pad, pad + insets.systemWindowInsetTop, pad, pad + insets.systemWindowInsetBottom)
            insets
        }
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            text = value; textSize = size
            setPadding(0, pad / 2, 0, pad / 2)
            root.addView(this)
        }
        text("Frame diagnostics", 24f)
        text("Record Baseline first, then Light rendering + high refresh, then Highest refresh rate only. Repeat the same scrolling, player open/close, and minimize/resume sequence with the same song and page.")
        text("Each run allows 3 seconds to settle, then captures 45 seconds. Overrides are temporary and reset when the run ends. Avoid changing Settings during a run.")
        modes = Spinner(this).apply {
            adapter = ArrayAdapter(this@FrameDiagnosticsActivity, android.R.layout.simple_spinner_dropdown_item,
                DiagnosticMode.entries.map { it.title })
            root.addView(this)
        }
        val description = text(DiagnosticMode.BASELINE.description)
        modes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                description.text = DiagnosticMode.entries[position].description
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val label = EditText(this).apply {
            hint = "Optional label: page / actions / playing or paused"
            setSingleLine(true)
            root.addView(this)
        }
        statusText = text("")
        fun button(value: String, action: () -> Unit) = Button(this).apply {
            text = value
            setOnClickListener { action() }
            root.addView(this)
        }
        start = button("Start 45-second recording") {
            if (FrameDiagnostics.status.value.recording || FrameDiagnostics.status.value.writing) return@button
            start.isEnabled = false
            FrameDiagnostics.start(this, DiagnosticMode.entries[modes.selectedItemPosition], label.text.toString())
            Toast.makeText(this, "Recording starts in 3 seconds and lasts 45 seconds.", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        stop = button("Stop and save now") { FrameDiagnostics.stop(this) }
        share = button("Share recordings") {
            share.isEnabled = false
            FrameDiagnostics.export(applicationContext) { result ->
                share.isEnabled = true
                result.fold(onSuccess = { file ->
                    val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = android.content.ClipData.newRawUri("Frame diagnostics", uri)
                    }, "Share frame recordings"))
                }, onFailure = {
                    Toast.makeText(this, it.message ?: "Could not export recordings", Toast.LENGTH_LONG).show()
                })
            }
        }
        button("Return to app") { finish() }
        text("The report contains device, rendering settings, frame timings and lifecycle events. It records the app's windows; the launcher's minimize animation requires an ADB system trace. There is no live FPS overlay during capture.", 14f)
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.PERFORMANCE_DIAGNOSTICS) main.post(refresh)
    }

    override fun onPause() {
        main.removeCallbacks(refresh)
        super.onPause()
    }
}

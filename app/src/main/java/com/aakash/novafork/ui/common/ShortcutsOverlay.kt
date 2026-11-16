package com.aakash.novafork.ui.common

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * The keyboard shortcuts help ("?"): a translucent sheet over the whole window, not a dialog.
 * A tap anywhere, "?", Esc or Back closes it. It is a plain view added to the activity's content
 * so the library (Compose) and the player share it.
 */
class ShortcutsOverlay(
    private val activity: ComponentActivity,
    private val title: String,
    private val sections: List<Section>,
) {
    class Section(val heading: String, val lines: List<Pair<String, String>>)

    private var view: View? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = dismiss()
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
    }

    val isShowing: Boolean get() = view != null

    fun toggle() = if (isShowing) dismiss() else show()

    /** True if the key was a close key and the overlay took it. */
    fun onKey(keyCode: Int): Boolean {
        if (!isShowing) return false
        if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_SLASH) {
            dismiss()
        }
        // Any key is swallowed while the sheet is up, so a stray key doesn't act behind it.
        return keyCode != KeyEvent.KEYCODE_BACK
    }

    fun dismiss() {
        val shown = view ?: return
        view = null
        backCallback.isEnabled = false
        shown.animate().alpha(0f).setDuration(160).withEndAction {
            (shown.parent as? ViewGroup)?.removeView(shown)
        }.start()
    }

    fun show() {
        if (view != null) return
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(40), dp(28), dp(40))
        }
        column.addView(
            TextView(activity).apply {
                text = title
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setPadding(0, 0, 0, dp(16))
            },
        )
        for (section in sections) {
            column.addView(
                TextView(activity).apply {
                    text = section.heading
                    setTextColor(Color.argb(0xB0, 0xFF, 0xFF, 0xFF))
                    textSize = 13f
                    setPadding(0, dp(14), 0, dp(6))
                },
            )
            for ((keys, what) in section.lines) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(5), 0, dp(5))
                }
                row.addView(
                    TextView(activity).apply {
                        text = keys
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        typeface = Typeface.MONOSPACE
                    },
                    LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                row.addView(
                    TextView(activity).apply {
                        text = what
                        setTextColor(Color.argb(0xE0, 0xFF, 0xFF, 0xFF))
                        textSize = 15f
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                column.addView(row)
            }
        }
        column.addView(
            TextView(activity).apply {
                text = "Tap anywhere, press ? or Esc to close"
                setTextColor(Color.argb(0x99, 0xFF, 0xFF, 0xFF))
                textSize = 13f
                setPadding(0, dp(22), 0, 0)
            },
        )

        val scroll = ScrollView(activity).apply { addView(column, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
        val sheet = FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(0xD8, 0, 0, 0))
            isClickable = true
            isFocusable = false
            setOnClickListener { dismiss() }
            // A centred column no wider than a reading line, so a tablet's sheet isn't a wall of text.
            addView(
                scroll,
                FrameLayout.LayoutParams(minOf(dp(560), activity.resources.displayMetrics.widthPixels), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL),
            )
            alpha = 0f
        }
        root.addView(sheet, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        sheet.animate().alpha(1f).setDuration(160).start()
        view = sheet
        backCallback.isEnabled = true
    }

    companion object {
        val Player = listOf(
            Section(
                "Playback",
                listOf(
                    "Space / K" to "Play or pause",
                    "← / →  or  J / L" to "Back or forward 10 seconds",
                    "0 – 9" to "Jump to that tenth of the video",
                    ", / ." to "Step one frame back or on",
                    "N / P" to "Next or previous",
                ),
            ),
            Section(
                "Sound and picture",
                listOf(
                    "↑ / ↓" to "Volume up or down",
                    "M" to "Mute",
                    "[ / ]" to "Slower or faster",
                    "F" to "Fit or zoom (fullscreen)",
                ),
            ),
        )

        val App = listOf(
            Section(
                "Library",
                listOf(
                    "Ctrl + 1 – 4" to "Video, Audio, Preferences, Search",
                    "/  or  Ctrl + F" to "Search",
                    "Tab, arrows, Enter" to "Move between items and open one",
                ),
            ),
        )
    }
}

package com.premiumtv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

/** Remote-only keyboard; works even when firmware has no D-pad friendly IME. */
object SearchKeyboard {
    fun show(activity: Activity, prefs: Preferences, initial: String, result: (String) -> Unit) {
        val context = activity
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(22), context.dp(6), context.dp(22), context.dp(8))
        }
        val input = EditText(context).apply {
            id = R.id.search_input
            setSingleLine(true)
            textSize = 18f
            setTextColor(Ui.WHITE)
            hint = context.getString(R.string.search_hint)
            setHintTextColor(Ui.MUTED)
            inputType = InputType.TYPE_CLASS_TEXT
            showSoftInputOnFocus = false
            setText(initial)
            setSelection(text.length)
        }
        root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(48)))
        val keyboard = GridLayout(context).apply { columnCount = 8 }
        root.addView(keyboard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        var arabic = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        fun insert(value: String) {
            val start = minOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
            val end = maxOf(input.selectionStart, input.selectionEnd).coerceAtLeast(start)
            input.text.replace(start, end, value)
        }
        fun render() {
            keyboard.removeAllViews()
            val letters = if (arabic) "ابتثجحخدذرزسشصضطظعغفقكلمنهويءأإةىؤئ" else "abcdefghijklmnopqrstuvwxyz"
            (letters + "0123456789").forEach { char ->
                val button = Ui.chip(context, char.toString(), prefs) { insert(char.toString()) }
                button.setPadding(0, 0, 0, 0)
                keyboard.addView(button, GridLayout.LayoutParams().apply {
                    width = 0; height = context.dp(35)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                })
            }
        }
        val tools = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        root.addView(tools, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(46)))
        fun tool(label: Int, action: () -> Unit) {
            tools.addView(Ui.chip(context, context.getString(label), prefs, action),
                LinearLayout.LayoutParams(0, context.dp(40), 1f).apply {
                    marginStart = context.dp(3); marginEnd = context.dp(3)
                })
        }
        tool(R.string.keyboard_language) { arabic = !arabic; render(); keyboard.getChildAt(0)?.requestFocus() }
        tool(R.string.space) { insert(" ") }
        tool(R.string.backspace) {
            val start = minOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
            val end = maxOf(input.selectionStart, input.selectionEnd).coerceAtLeast(start)
            if (start != end) input.text.delete(start, end)
            else if (start > 0) input.text.delete(start - 1, start)
        }
        tool(R.string.clear) { input.setText("") }
        render()
        val dialog = AlertDialog.Builder(context).setTitle(R.string.search).setView(root)
            .setPositiveButton(R.string.search_done) { _, _ -> result(input.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.search_clear) { _, _ -> result("") }.create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(minOf(context.resources.displayMetrics.widthPixels - context.dp(64), context.dp(620)),
                ViewGroup.LayoutParams.WRAP_CONTENT)
            keyboard.getChildAt(0)?.requestFocus()
        }
        dialog.show()
    }
}

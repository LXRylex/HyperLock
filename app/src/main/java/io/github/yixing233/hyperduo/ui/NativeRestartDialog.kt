// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.yixing233.hyperduo.R

private class RestartDialogViews(val window: Dialog, val choices: List<CheckBox>, val clear: Button, val confirm: Button)

/** Native dialog window with MIUI-style selection rows and buttons. */
@Composable
internal fun NativeRestartDialog(controller: RestartController, show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentDismiss by rememberUpdatedState(onDismiss)
    val state = controller.state
    var views by remember { mutableStateOf<RestartDialogViews?>(null) }
    DisposableEffect(show, context) {
        if (!show) return@DisposableEffect onDispose { }
        val native = buildRestartDialog(context, controller)
        native.window.setOnDismissListener { currentDismiss() }
        native.window.show()
        native.window.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.65f)
            setGravity(Gravity.BOTTOM)
            decorView.setPadding(dp(context, 12), 0, dp(context, 12), dp(context, 4))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        views = native
        onDispose {
            native.window.setOnDismissListener(null)
            native.window.dismiss()
            if (views === native) views = null
        }
    }
    LaunchedEffect(state, views) {
        val target = views ?: return@LaunchedEffect
        val running = state is RestartState.Running
        target.window.setCancelable(!running)
        target.window.setCanceledOnTouchOutside(!running)
        target.choices.forEach { it.isEnabled = !running }
        target.clear.isEnabled = !running
        target.confirm.isEnabled = !running && target.choices.any { it.isChecked }
        target.confirm.alpha = if (target.confirm.isEnabled) 1f else .48f
        target.confirm.text = context.getString(if (running) R.string.restart_running else R.string.restart_confirm)
        if (state is RestartState.Failed) {
            Toast.makeText(context, listOfNotNull(context.getString(R.string.restart_failed), state.detail).joinToString(" · "), Toast.LENGTH_LONG).show()
        }
    }
}

private fun buildRestartDialog(context: Context, controller: RestartController): RestartDialogViews {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val surface = if (dark) Color.rgb(32,32,32) else Color.rgb(250,250,250)
    val ink = if (dark) Color.WHITE else Color.rgb(28,28,28)
    val muted = if (dark) Color.rgb(76,76,76) else Color.rgb(219,219,219)
    val selected = Color.rgb(52,128,255)
    val dialog = Dialog(context)
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24))
        background = rounded(surface, dp(context, 30).toFloat())
    }
    body.addView(TextView(context).apply {
        text = context.getString(R.string.restart_title)
        setTextColor(ink)
        textSize = 20f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(-1, dp(context, 48)))
    val choices = listOf("System UI", "System launcher", "Settings", "Security").map { label ->
        val check = CheckBox(context).apply {
            contentDescription = label
            buttonDrawable = indicators(context, selected, muted)
            buttonTintList = null
            setPadding(0,0,0,0)
            minWidth = 0
            minimumWidth = 0
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(context, 4), 0, dp(context, 4), 0)
            setOnClickListener { if (check.isEnabled) check.isChecked = !check.isChecked }
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(ink)
            textSize = 19f
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(check, LinearLayout.LayoutParams(dp(context, 32), dp(context, 48)))
        body.addView(row, LinearLayout.LayoutParams(-1, dp(context, 64)))
        check
    }
    val buttons = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(context, 16), 0, 0)
    }
    fun button(text: String, color: Int, textColor: Int) = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setTextColor(textColor)
        minHeight = 0
        minimumHeight = 0
        background = rounded(color, dp(context, 15).toFloat())
    }
    val clear = button("Deselect all", muted, ink)
    val confirm = button(context.getString(R.string.restart_confirm), selected, Color.WHITE)
    val leftParams = LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginEnd = dp(context, 6) }
    val rightParams = LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginStart = dp(context, 6) }
    buttons.addView(clear, leftParams); buttons.addView(confirm, rightParams)
    body.addView(buttons, LinearLayout.LayoutParams(-1, dp(context, 66)))
    dialog.setContentView(body)
    fun refreshConfirm() {
        confirm.isEnabled = choices.any { it.isChecked }
        confirm.alpha = if (confirm.isEnabled) 1f else .48f
    }
    choices.forEach { choice -> choice.setOnCheckedChangeListener { _, _ -> refreshConfirm() } }
    clear.setOnClickListener { choices.forEach { it.isChecked = false } }
    confirm.setOnClickListener { if (confirm.isEnabled) controller.restart(choices[0].isChecked, choices[1].isChecked, choices[2].isChecked, choices[3].isChecked) }
    refreshConfirm()
    return RestartDialogViews(dialog, choices, clear, confirm)
}

private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
private fun indicators(context: Context, selected: Int, muted: Int) = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_checked), CircleCheckDrawable(dp(context, 26), selected, true))
    addState(intArrayOf(), CircleCheckDrawable(dp(context, 26), muted, false))
}
private class CircleCheckDrawable(private val size: Int, private val color: Int, private val checked: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun draw(canvas: Canvas) {
        val b = bounds
        val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val radius = minOf(b.width(), b.height()) * .5f
        paint.style = Paint.Style.FILL; paint.color = color
        canvas.drawCircle(cx,cy,radius,paint)
        if (checked) {
            paint.color = Color.WHITE; paint.style = Paint.Style.STROKE
            paint.strokeWidth = radius * .23f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            val path = Path().apply { moveTo(cx-radius*.48f,cy); lineTo(cx-radius*.12f,cy+radius*.36f); lineTo(cx+radius*.52f,cy-radius*.36f) }
            canvas.drawPath(path,paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}


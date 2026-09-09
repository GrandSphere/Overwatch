package com.grandsphere.overwatch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.RemoteViews
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.R
import com.grandsphere.overwatch.runtime.NotificationActionReceiver
import com.grandsphere.overwatch.runtime.OverwatchNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import android.graphics.Color as AndroidColor

class PanicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, panicViews(context))
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PanicWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                PanicWidgetProvider().onUpdate(context, mgr, ids)
            }
        }
    }
}

class OverwatchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val names = runBlocking(Dispatchers.IO) {
            val app = OverwatchApp.from(context)
            val list = app.engine.configs.ifEmpty { app.repository.list() }
            list.associate { it.id to it.name }
        }
        appWidgetIds.forEach { id ->
            val configId = OverwatchWidgetStore.configId(context, id)
            val name = names[configId]
            val letter = name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            appWidgetManager.updateAppWidget(id, overwatchViews(context, id, configId, letter))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { OverwatchWidgetStore.delete(context, it) }
    }

    companion object {
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, OverwatchWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                OverwatchWidgetProvider().onUpdate(context, mgr, ids)
            }
        }
    }
}

fun refreshOverwatchWidgets(context: Context) {
    OverwatchWidgetProvider.refreshAll(context)
    PanicWidgetProvider.refreshAll(context)
}

internal fun panicViews(context: Context): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_panic)
    views.setImageViewBitmap(R.id.widget_badge, letterBadgeBitmap(context, "P", PANIC_ACCENT))
    val intent = Intent(context, NotificationActionReceiver::class.java)
        .setAction(OverwatchNotifications.ACTION_PANIC)
    val pi = PendingIntent.getBroadcast(
        context,
        10,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_root, pi)
    return views
}

internal fun overwatchViews(context: Context, appWidgetId: Int, configId: Long, letter: String): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_overwatch)
    val accent = OverwatchApp.from(context).latestSettings.widgetArgb
    views.setImageViewBitmap(R.id.widget_badge, letterBadgeBitmap(context, letter, accent))
    val intent = Intent(context, NotificationActionReceiver::class.java)
        .setAction(OverwatchNotifications.ACTION_ENABLE)
        .putExtra(OverwatchNotifications.EXTRA_CONFIG_ID, configId)
    val pi = PendingIntent.getBroadcast(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_root, pi)
    return views
}

private const val PANIC_ACCENT = 0xFFFF6B6B.toInt()

private fun letterBadgeBitmap(context: Context, letter: String, accentArgb: Int): Bitmap {
    val res = context.resources
    val textPx = res.getDimension(R.dimen.widget_letter_size)
    val strokePx = 1.5f * res.displayMetrics.density
    val pad = textPx * 0.35f
    val size = (textPx + pad * 2f + strokePx).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.density = res.displayMetrics.densityDpi
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val radius = size / 2f - strokePx / 2f
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.BLACK
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radius, fill)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    canvas.drawCircle(cx, cy, radius, ring)
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentArgb
        textSize = textPx
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    val textY = cy - (textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f
    canvas.drawText(letter, cx, textY, textPaint)
    return bitmap
}

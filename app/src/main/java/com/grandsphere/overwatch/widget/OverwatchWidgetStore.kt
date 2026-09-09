package com.grandsphere.overwatch.widget

import android.content.Context

object OverwatchWidgetStore {
    private const val PREFS = "overwatch_widgets"
    private const val KEY = "cfg_"

    fun save(context: Context, appWidgetId: Int, configId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY + appWidgetId, configId)
            .apply()
    }

    fun configId(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY + appWidgetId, -1L)

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY + appWidgetId)
            .apply()
    }
}

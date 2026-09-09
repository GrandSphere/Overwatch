package com.grandsphere.overwatch.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.grandsphere.overwatch.OverwatchApp
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.ui.theme.OverwatchTheme

class OverwatchWidgetConfigureActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        setResult(Activity.RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val app = OverwatchApp.from(this)
        setContent {
            val settings = app.latestSettings
            OverwatchTheme(
                light = settings.lightTheme,
                cardArgb = settings.darkCardArgb,
                actionArgb = settings.actionArgb,
            ) {
                var configs by remember { mutableStateOf(emptyList<OverwatchConfig>()) }
                LaunchedEffect(Unit) {
                    configs = withContext(Dispatchers.IO) { app.repository.list() }
                }
                ConfigureList(
                    configs = configs,
                    onPick = { config ->
                        OverwatchWidgetStore.save(this, appWidgetId, config.id)
                        val mgr = AppWidgetManager.getInstance(this)
                        val letter = config.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        mgr.updateAppWidget(
                            appWidgetId,
                            overwatchViews(this, appWidgetId, config.id, letter),
                        )
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigureList(
    configs: List<OverwatchConfig>,
    onPick: (OverwatchConfig) -> Unit,
) {
    LazyColumn {
        item {
            Text(
                "Choose Overwatch",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(configs, key = { it.id }) { config ->
            Text(
                config.name,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(config) }
                    .padding(16.dp),
            )
        }
    }
}

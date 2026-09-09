package com.grandsphere.overwatch

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grandsphere.overwatch.ui.MainViewModel
import com.grandsphere.overwatch.ui.OverwatchRoot
import java.lang.ref.WeakReference

class MainActivity : FragmentActivity() {
    private val app get() = application as OverwatchApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        com.grandsphere.overwatch.runtime.VerboseLog.d(
            "UI",
            "MainActivity onCreate finish=${intent?.getBooleanExtra(EXTRA_FINISH, false)}",
        )
        if (intent?.getBooleanExtra(EXTRA_FINISH, false) == true) {
            finishAffinity()
            return
        }
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel(factory = MainViewModel.factory(app))
            OverwatchRoot(vm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FINISH, false)) {
            finishAffinity()
        }
    }

    override fun onResume() {
        super.onResume()
        com.grandsphere.overwatch.runtime.VerboseLog.d("UI", "MainActivity onResume")
    }

    override fun onPause() {
        com.grandsphere.overwatch.runtime.VerboseLog.d("UI", "MainActivity onPause")
        super.onPause()
    }

    override fun onDestroy() {
        com.grandsphere.overwatch.runtime.VerboseLog.d("UI", "MainActivity onDestroy finishing=$isFinishing")
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = app.hardware.onKeyDown(keyCode, event, MainActivitySettings.latest)
        return handled || super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_FINISH = "finish_affinity"

        @Volatile
        private var current: WeakReference<MainActivity>? = null

        fun finishAffinityIfPresent(): Boolean {
            val activity = current?.get() ?: return false
            activity.finishAffinity()
            return true
        }
    }
}

object MainActivitySettings {
    @Volatile
    var latest: com.grandsphere.overwatch.domain.model.AppSettings =
        com.grandsphere.overwatch.domain.model.AppSettings()
}

package com.grandsphere.overwatch.domain.hardware

import android.view.KeyEvent
import com.grandsphere.overwatch.domain.engine.OverwatchEngine
import com.grandsphere.overwatch.domain.model.HardwareKeyOption
import com.grandsphere.overwatch.domain.model.AppSettings

/**
 * Hook for in-app keys now and AccessibilityService later.
 * A future service should call [OverwatchEngine] the same way [InAppRestrictedHardware] does.
 */
interface RestrictedHardware {
    fun bind(engine: OverwatchEngine)
    fun onKeyDown(keyCode: Int, event: KeyEvent, settings: AppSettings): Boolean
}

object NoOpRestrictedHardware : RestrictedHardware {
    override fun bind(engine: OverwatchEngine) = Unit
    override fun onKeyDown(keyCode: Int, event: KeyEvent, settings: AppSettings): Boolean = false
}

class InAppRestrictedHardware : RestrictedHardware {
    private var engine: OverwatchEngine? = null
    private var lastVolumeUpAt = 0L

    override fun bind(engine: OverwatchEngine) {
        this.engine = engine
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent, settings: AppSettings): Boolean {
        val eng = engine ?: return false
        if (event.repeatCount != 0) return false

        val panicKey = settings.panicHardwareKey
        if (panicKey == HardwareKeyOption.VOLUME_UP && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            eng.panic()
            return true
        }
        if (panicKey == HardwareKeyOption.VOLUME_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            eng.panic()
            return true
        }
        if (panicKey == HardwareKeyOption.VOLUME_UP_DOUBLE && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val now = System.currentTimeMillis()
            if (now - lastVolumeUpAt < 500) {
                lastVolumeUpAt = 0L
                eng.panic()
            } else {
                lastVolumeUpAt = now
            }
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            return eng.onVolumeDown()
        }
        return false
    }
}

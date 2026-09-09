package com.grandsphere.overwatch.runtime

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class CallLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        if (number.isNotEmpty()) {
            val uri = Uri.fromParts("tel", number, null)
            val granted = checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val action = if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL
            runCatching {
                startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_NUMBER = "number"

        fun intent(context: android.content.Context, number: String): Intent =
            Intent(context, CallLaunchActivity::class.java)
                .putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

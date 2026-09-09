package com.grandsphere.overwatch.ui.chrome

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.grandsphere.overwatch.runtime.VerboseLog

object FingerprintAuth {
    fun canAuthenticate(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(
        activity: FragmentActivity,
        title: String = "Fingerprint",
        onSuccess: () -> Unit,
        onDismiss: () -> Unit = {},
        onFailed: (String) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    VerboseLog.ok("Perms", "fingerprint success title=$title")
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    VerboseLog.d("Perms", "fingerprint error=$errorCode $errString title=$title")
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        onFailed(errString.toString())
                    }
                    onDismiss()
                }

                override fun onAuthenticationFailed() {
                    VerboseLog.fail("Perms", "fingerprint not recognized title=$title")
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText("Back")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
        )
        VerboseLog.d("Perms", "fingerprint prompt shown title=$title")
    }
}

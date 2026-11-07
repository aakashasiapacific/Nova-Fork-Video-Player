package com.aakash.novafork.ui.common

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import android.content.Intent

/**
 * Asks for the fingerprint, face or screen lock with the platform's own prompt (MainActivity is a
 * ComponentActivity, so androidx.biometric is not an option). Android 10+: BiometricPrompt with a
 * strong biometric or the device credential. Android 8-9: the screen-lock confirmation screen.
 */
internal object DeviceAuth {
    /** Whether the device has a screen lock (or an enrolled strong biometric) to ask for. */
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return false
            val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return manager.canAuthenticate(allowed) == BiometricManager.BIOMETRIC_SUCCESS
        }
        return context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }

    /**
     * Shows the prompt. [onResult] gets true once the person is confirmed and false on a cancel
     * or an error. [legacy] launches the lock-screen confirmation on Android 8-9. The returned
     * signal cancels the prompt (when the page goes away).
     */
    fun prompt(
        context: Context,
        title: String,
        legacy: ManagedActivityResultLauncher<Intent, ActivityResult>,
        onResult: (Boolean) -> Unit,
    ): CancellationSignal? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val intent = context.getSystemService(KeyguardManager::class.java)
                ?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) {
                onResult(false)
            } else {
                legacy.launch(intent)
            }
            return null
        }
        val signal = CancellationSignal()
        val builder = BiometricPrompt.Builder(context).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            signal,
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onResult(true)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onResult(false)
            },
        )
        return signal
    }
}

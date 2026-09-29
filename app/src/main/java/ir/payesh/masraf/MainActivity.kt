package ir.payesh.masraf

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private var authed by mutableStateOf(false)
    private var authMessage by mutableStateOf<String?>(null)
    private var resumeTick by mutableIntStateOf(0)
    private var prompting = false
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PayeshTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (authed) HomeScreen(resumeTick) else GateScreen(authMessage) { authenticate() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // اگر بیش از ۳۰ ثانیه از برنامه بیرون بوده‌ای، دوباره احراز هویت لازم است
        if (authed && SystemClock.elapsedRealtime() - stoppedAt > 30_000) authed = false
        if (!authed) authenticate()
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** اثر انگشت / چهره / پین / پترن / رمز — همان قفل خود گوشی. */
    private fun authenticate() {
        if (prompting) return
        val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            // گوشی قفل صفحه ندارد؛ چیزی برای تأیید وجود ندارد
            authed = true
            return
        }
        prompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    authMessage = null
                    authed = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    authMessage = errString.toString()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود به پایش مصرف")
            .setSubtitle("برای ادامه، هویت خود را تأیید کنید")
            .setAllowedAuthenticators(allowed)
            .build()
        prompt.authenticate(info)
    }
}

package com.varisahayak.app

import android.content.Context
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.varisahayak.app.navigation.VariSahayakApp
import com.varisahayak.core.designsystem.VariSahayakTheme
import com.varisahayak.core.designsystem.VariTheme
import com.varisahayak.core.locale.AppLocale
import com.varisahayak.core.locale.AppLocaleStore
import com.varisahayak.feature.notifications.NotificationDeepLinkBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single activity host.
 *
 * launchMode is singleTask (see the manifest) so a notification tap reuses the running
 * task rather than stacking a second copy of the app over the volunteer's current work.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var deepLinkBus: NotificationDeepLinkBus

    /**
     * Applies the stored language before any resource is resolved.
     *
     * This has to happen here, not in `onCreate`: by the time `onCreate` runs, the
     * activity's resources have already been created against the system locale, and
     * anything read before a later override would be in the wrong language.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pinned to light bars, for the same reason the theme is pinned to light.
        // `enableEdgeToEdge()` with no arguments follows the system, so on a device in dark
        // mode it would draw white status icons over our white frosted app bar and make the
        // clock and battery vanish.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(TRANSPARENT, TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // The cold-start half of the deep link: the tap that launched the process arrives
        // here, long before the NavHost exists. The bus holds it until navigation is ready.
        deepLinkBus.offer(intent)

        setContent {
            // Light, regardless of the device setting.
            //
            // This is a daylight instrument. The whole palette — and every contrast ratio
            // it was tuned against — assumes dark text on a light ground, which is what
            // survives direct sun on a cheap LCD at full brightness. Following the system
            // into dark mode would hand a volunteer standing in a field at noon the one
            // theme that is unreadable there.
            //
            // The dark tokens in VariColors are kept and still correct; flipping this flag
            // is all that is needed if a night mode is ever wanted for pre-dawn walking.
            VariSahayakTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    // The canvas is slate, not white: every operational card in the app is
                    // white, and a white page underneath them would flatten the whole
                    // elevation system into nothing.
                    color = VariTheme.colors.canvas,
                ) {
                    VariSahayakApp(
                        currentLocale = AppLocaleStore.current(this),
                        onLocaleChange = ::applyLocale,
                    )
                }
            }
        }
    }

    /**
     * The warm half of the deep link.
     *
     * With singleTask, a tap while the app is already running delivers the intent here
     * rather than through `onCreate`. Both paths feed the same bus.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkBus.offer(intent)
    }

    /**
     * Persists the choice and restarts the activity.
     *
     * `recreate()` rather than a live configuration swap because string resources are
     * resolved at composition and there is no supported way to re-resolve them in place
     * across an already-built hierarchy. The restart is fast and the navigation back stack
     * survives it.
     */
    private fun applyLocale(locale: AppLocale) {
        if (AppLocaleStore.current(this) == locale) return
        AppLocaleStore.save(this, locale)
        recreate()
    }
}

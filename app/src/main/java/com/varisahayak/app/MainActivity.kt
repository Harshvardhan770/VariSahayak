package com.varisahayak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.varisahayak.app.navigation.VariSahayakApp
import com.varisahayak.core.designsystem.VariSahayakTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single activity host.
 *
 * launchMode is singleTask (see the manifest) so a notification tap reuses the running
 * task rather than stacking a second copy of the app over the volunteer's current work.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VariSahayakTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VariSahayakApp()
                }
            }
        }
    }
}

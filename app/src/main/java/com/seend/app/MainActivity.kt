package com.seend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.seend.app.nav.SeendNavHost
import com.seend.app.ui.theme.SeendTheme

/**
 * Actividad única. Todo el routing lo hace Compose Navigation dentro de
 * [SeendNavHost]. El splash nativo se instala una sola vez.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeendTheme {
                SeendNavHost()
            }
        }
    }
}

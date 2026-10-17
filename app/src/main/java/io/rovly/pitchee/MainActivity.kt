package io.rovly.pitchee

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.rovly.pitchee.feature.settings.AppLanguage
import io.rovly.pitchee.ui.PitcheeApp
import io.rovly.pitchee.ui.theme.PitcheeTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguage.restore(this)
        enableEdgeToEdge()
        setContent {
            PitcheeTheme {
                PitcheeApp()
            }
        }
    }
}

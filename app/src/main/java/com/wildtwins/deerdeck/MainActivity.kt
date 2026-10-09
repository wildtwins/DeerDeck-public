package com.wildtwins.deerdeck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtwins.deerdeck.ui.DeerDeckApp
import com.wildtwins.deerdeck.ui.theme.DeerDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as DeerDeckApplication).repository
        val settingsRepository = (application as DeerDeckApplication).settingsRepository
        setContent {
            val themeMode = settingsRepository.themeMode.collectAsStateWithLifecycle()
            DeerDeckTheme(themeMode = themeMode.value) {
                DeerDeckApp(
                    repository = repository,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}

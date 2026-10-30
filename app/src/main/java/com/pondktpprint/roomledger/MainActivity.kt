package com.pondktpprint.roomledger

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import com.pondktpprint.roomledger.presentation.RoomLedgerScreen
import com.pondktpprint.roomledger.ui.theme.RoomLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as RoomLedgerApplication).repository
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                val systemBackground = if (darkTheme) Color.rgb(17, 26, 22) else Color.rgb(250, 250, 247)
                window.statusBarColor = systemBackground
                window.navigationBarColor = systemBackground
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !darkTheme
            }
            RoomLedgerTheme {
                RoomLedgerScreen(repository = repository)
            }
        }
    }
}

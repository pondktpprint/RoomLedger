package com.pondktpprint.roomledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pondktpprint.roomledger.presentation.RoomLedgerScreen
import com.pondktpprint.roomledger.ui.theme.RoomLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as RoomLedgerApplication).repository
        setContent {
            RoomLedgerTheme {
                RoomLedgerScreen(repository = repository)
            }
        }
    }
}

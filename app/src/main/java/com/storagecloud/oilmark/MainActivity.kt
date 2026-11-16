package com.storagecloud.oilmark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.storagecloud.oilmark.ui.OilmarkNavigation
import com.storagecloud.oilmark.ui.common.Session
import com.storagecloud.oilmark.ui.connect.ConnectScreen
import com.storagecloud.oilmark.ui.theme.OilmarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = (application as OilmarkApp).settings

        setContent {
            OilmarkTheme {
                // null = saved settings haven't been read from disk yet (a few milliseconds).
                val settings by store.settings.collectAsStateWithLifecycle(initialValue = null)
                val current = settings
                Surface(Modifier.fillMaxSize()) {
                    when {
                        current == null -> {}
                        current.server == null -> ConnectScreen(store)
                        // key: connecting to a different server starts over with a fresh garage.
                        else -> key(current.server) {
                            Session(current, store) { OilmarkNavigation() }
                        }
                    }
                }
            }
        }
    }
}

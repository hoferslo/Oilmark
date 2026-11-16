package com.storagecloud.oilmark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.storagecloud.oilmark.ui.OilmarkNavigation
import com.storagecloud.oilmark.ui.common.Session
import com.storagecloud.oilmark.ui.connect.ConnectScreen
import com.storagecloud.oilmark.ui.theme.OilmarkTheme
import com.storagecloud.oilmark.widget.OilmarkWidget
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A screen the widget or a notification asked for, until navigation has opened it. */
    private var launch by mutableStateOf<Launch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a real start; after a rotation the screen is already open.
        if (savedInstanceState == null) launch = Launch.from(intent)
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
                            Session(current, store) { OilmarkNavigation(launch, onLaunched = { launch = null }) }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Launch.from(intent)?.let { launch = it }
    }

    override fun onStop() {
        super.onStop()
        // Whatever was added or changed shows on the home-screen widget right away.
        lifecycleScope.launch { runCatching { OilmarkWidget().updateAll(applicationContext) } }
    }
}

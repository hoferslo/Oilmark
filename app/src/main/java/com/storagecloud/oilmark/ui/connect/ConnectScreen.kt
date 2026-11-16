package com.storagecloud.oilmark.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OilBarrel
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.storagecloud.oilmark.data.ApiException
import com.storagecloud.oilmark.data.LubeLoggerApi
import com.storagecloud.oilmark.data.Server
import com.storagecloud.oilmark.data.SettingsStore
import com.storagecloud.oilmark.ui.common.TextInput
import kotlinx.coroutines.launch

/** First screen: asks where the LubeLogger server is, checks it answers, then saves it. */
@Composable
fun ConnectScreen(store: SettingsStore) {
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun connect() {
        if (url.isBlank()) {
            error = "Enter your server's address first"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                val server = Server(cleanUrl(url), username.trim(), password)
                val api = LubeLoggerApi(server)
                api.whoAmI() // fails if the address or login is wrong
                store.connect(server, currency = api.serverInfo().currencySymbol)
                // Saving flips the app over to the garage (see MainActivity).
            } catch (e: ApiException) {
                error = e.message
            }
            busy = false
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.OilBarrel, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Oilmark", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Connect to your LubeLogger server to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server address *") },
                placeholder = { Text("http://192.168.1.10:8080") },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            TextInput(
                "Username (if your server has a login)", username, { username = it },
                icon = Icons.Outlined.Person,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Button(
                onClick = ::connect,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Text(if (busy) "Connecting…" else "Connect")
            }
        }
    }
}

/** "192.168.1.10:8080/" -> "http://192.168.1.10:8080" */
private fun cleanUrl(typed: String): String {
    val trimmed = typed.trim().trimEnd('/')
    return if ("://" in trimmed) trimmed else "http://$trimmed"
}

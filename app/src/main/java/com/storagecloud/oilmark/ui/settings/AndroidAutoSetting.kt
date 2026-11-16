package com.storagecloud.oilmark.ui.settings

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.storagecloud.oilmark.car.CAR_DATA_PERMISSIONS
import com.storagecloud.oilmark.car.OilmarkCarService
import com.storagecloud.oilmark.ui.common.ToggleInput

/**
 * The switch that makes Oilmark appear in Android Auto.
 *
 * It enables or disables the Android Auto service itself, and Android remembers that - so
 * while it is off, none of the Android Auto code can run, and once on it stays on (also
 * after restarts and updates) until it is switched off here.
 */
@Composable
fun AndroidAutoSetting() {
    val context = LocalContext.current
    val packages = context.packageManager
    val service = remember { ComponentName(context, OilmarkCarService::class.java) }
    var enabled by remember {
        mutableStateOf(
            packages.getComponentEnabledSetting(service) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
    }
    // Android Auto only hands over car data the user has allowed; ask right when it is switched on.
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    ToggleInput(
        "Show Oilmark in Android Auto", enabled,
        onChange = { on ->
            packages.setComponentEnabledSetting(
                service,
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            enabled = on
            if (on) askPermissions.launch(CAR_DATA_PERMISSIONS)
        },
        icon = Icons.Outlined.DirectionsCar,
    )
}

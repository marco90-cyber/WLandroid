package com.marco.wlandroid

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

data class LAction(val id: String, val label: String, val icon: ImageVector)

val LAUNCHER_ACTIONS = listOf(
    LAction("apps", "Elenco app", Icons.Outlined.Apps),
    LAction("settings", "Impostazioni launcher", Icons.Outlined.Settings),
    LAction("notif", "Pannello notifiche", Icons.Outlined.Notifications),
    LAction("torch", "Torcia", Icons.Outlined.FlashlightOn),
    LAction("sys", "Impostazioni di sistema", Icons.Outlined.SettingsApplications),
    LAction("wifi", "Wi-Fi", Icons.Outlined.Wifi),
    LAction("bt", "Bluetooth", Icons.Outlined.Bluetooth)
)

fun actionById(id: String): LAction? = LAUNCHER_ACTIONS.firstOrNull { it.id == id }

private var torchOn = false

fun toggleTorch(ctx: Context) {
    try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return
        torchOn = !torchOn
        cm.setTorchMode(id, torchOn)
    } catch (e: Exception) {
        torchOn = false
    }
}

/** Apre il pannello delle notifiche (usa un'API nascosta: su alcuni telefoni potrebbe non fare nulla). */
fun expandNotifications(ctx: Context) {
    try {
        val sb = ctx.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    } catch (e: Exception) {
    }
}

fun openSettings(ctx: Context, action: String) {
    try {
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

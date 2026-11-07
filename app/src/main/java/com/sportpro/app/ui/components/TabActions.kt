package com.sportpro.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** "+" top-left (create, depending on role). */
@Composable
fun CreateAction(onCreate: (() -> Unit)?) {
    if (onCreate != null) {
        IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = "Crear partido") }
    }
}

/** Unread notifications for the bell badge (provided by the main shell). */
val LocalUnreadCount = staticCompositionLocalOf { 0 }

/** Bell top-right: Notificaciones y Avisos, with unread badge. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellAction(onBell: () -> Unit) {
    val unread = LocalUnreadCount.current
    IconButton(onClick = onBell) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) } }) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notificaciones y avisos ($unread sin leer)")
        }
    }
}

package com.sportpro.app.ui.components
import androidx.compose.ui.res.stringResource
import com.sportpro.app.R

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
        IconButton(onClick = onCreate) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tab_actions_crear_partido)) }
    }
}

/** Cantidad de notificaciones sin leer para el globo de la campana (la entrega MainShell). */
val LocalUnreadCount = staticCompositionLocalOf { 0 }

/** Campana arriba a la derecha: abre Notificaciones y muestra cuántas hay sin leer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BellAction(onBell: () -> Unit) {
    val unread = LocalUnreadCount.current
    IconButton(onClick = onBell) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else unread.toString()) } }) {
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.tab_actions_notificaciones_y_avisos_x_sin, unread))
        }
    }
}

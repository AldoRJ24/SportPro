package com.sportpro.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sportpro.app.R

/** Maps the catalog `icon` key (configurable per academy) to an own vector drawing. */
@DrawableRes
fun eventIconRes(iconKey: String?, code: String? = null): Int = when (iconKey ?: code) {
    "ball", "goal" -> R.drawable.ic_ev_ball
    "penalty" -> R.drawable.ic_ev_penalty
    "card_yellow", "yellow_card" -> R.drawable.ic_ev_card_yellow
    "card_red", "red_card" -> R.drawable.ic_ev_card_red
    "swap", "substitution" -> R.drawable.ic_ev_swap
    "foul" -> R.drawable.ic_ev_foul
    "corner" -> R.drawable.ic_ev_corner
    "free_kick" -> R.drawable.ic_ev_free_kick
    "goal_kick" -> R.drawable.ic_ev_goal_kick
    "throw_in" -> R.drawable.ic_ev_throw_in
    "offside" -> R.drawable.ic_ev_offside
    "glove", "save" -> R.drawable.ic_ev_glove
    "clearance" -> R.drawable.ic_ev_clearance
    "whistle", "whistle_end", "match_start", "match_end", "second_half_start" -> R.drawable.ic_ev_whistle
    "half_time", "first_half_end" -> R.drawable.ic_ev_half_time
    else -> R.drawable.ic_ev_generic
}

private fun keepsOwnColors(iconKey: String?, code: String?): Boolean =
    (iconKey ?: code) in setOf("card_yellow", "yellow_card", "card_red", "red_card")

@Composable
fun EventIcon(
    iconKey: String?,
    code: String?,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = LocalContentColor.current,
) {
    val res = eventIconRes(iconKey, code)
    if (keepsOwnColors(iconKey, code)) {
        Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
    } else {
        Icon(painterResource(res), contentDescription = null, modifier = modifier.size(size), tint = tint)
    }
}

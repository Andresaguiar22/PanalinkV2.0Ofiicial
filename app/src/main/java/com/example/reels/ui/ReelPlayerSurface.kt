package com.example.reels.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Binds a [PlayerView] to [player].
 *
 * The caller passes an observed (state-backed) player reference. When it changes
 * from null (still acquiring) to a real player, the [AndroidView.update] rebinds —
 * otherwise the view would stay stuck on the first frame / black until the next
 * recomposition.
 */
@Composable
fun ReelPlayerSurface(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                useController = false
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        }
    )
}
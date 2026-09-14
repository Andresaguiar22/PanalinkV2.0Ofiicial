package com.example.reels.ui

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R

/**
 * Binds a [PlayerView] to [player].
 *
 * The caller passes an observed (state-backed) player reference. When it changes
 * from null (still acquiring) to a real player, the [AndroidView.update] rebinds —
 * otherwise the view would stay stuck on the first frame / black until the next
 * recomposition.
 *
 * The view is inflated from [R.layout.view_reel_player], which pins the surface to
 * a TextureView: the feed lives inside a VerticalPager, and a SurfaceView there
 * neither moves with the swipe (its surface is composited by the system, not by
 * the pager) nor lets the Compose overlay draw above it.
 */
@Composable
fun ReelPlayerSurface(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.view_reel_player, null) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        }
    )
}
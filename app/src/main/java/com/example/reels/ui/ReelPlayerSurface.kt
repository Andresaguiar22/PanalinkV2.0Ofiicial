package com.example.reels.ui

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.reels.engine.ReelPlayerPool

/**
 * Binds a [PlayerView] to the player that the pool already holds for [reelId].
 *
 * The parent is responsible for acquiring the reel in the pool before this is
 * composed. The AndroidView re-attaches only when the bound player instance
 * changes, so swipes never flicker or black-screen.
 */
@Composable
fun ReelPlayerSurface(
    reelId: String,
    pool: ReelPlayerPool,
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
                player = pool.playerFor(reelId)
            }
        },
        update = { view ->
            val fresh = pool.playerFor(reelId)
            if (view.player !== fresh) view.player = fresh
        }
    )
}
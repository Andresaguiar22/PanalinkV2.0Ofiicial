package com.example.ui.call

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CallActionButton defines a highly polished circular interactive control
 * with proper touch targets, color schemes, and bouncy spring interactions.
 *
 * The click is delivered through the IconButton's own onClick (previously the
 * IconButton had an empty onClick `{}` while the click was wired only through
 * the bounceClick modifier, which got shadowed by IconButton's internal
 * clickable — so the buttons silently did nothing). The press-scale bounce is
 * now driven by the same interactionSource so the animation and the click
 * share a single, reliable touch path.
 */
@Composable
fun CallActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.5f),
    contentColor: Color = Color.White,
    size: Dp = 56.dp,
    iconSize: Dp = 26.dp,
    label: String? = null,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "callBtnScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        val buttonModifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .let { if (testTag != null) it.testTag(testTag) else it }

        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = buttonModifier
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }

        if (label != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

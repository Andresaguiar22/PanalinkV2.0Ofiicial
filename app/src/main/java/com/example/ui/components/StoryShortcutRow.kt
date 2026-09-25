package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserStateWithUser
import com.example.ui.settings.ios.IosFont
import com.example.ui.settings.ios.IosSettingsColors

private const val SEGMENT_GAP_DEGREES = 7f
private val RingOuterSize = 68.dp
private val RingStroke = 3.dp
private val AvatarSize = 56.dp
private val ItemWidth = 76.dp

/**
 * Anillo segmentado estilo Instagram: un arco por historia publicada, de modo
 * que el propio anillo indica cuantas historias tiene el usuario. Cada arco se
 * pinta verde si esa historia sigue sin verse y gris si ya fue vista.
 */
@Composable
fun StorySegmentRing(
    segmentColors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = RingOuterSize,
    strokeWidth: Dp = RingStroke
) {
    Canvas(modifier = modifier.size(size)) {
        if (segmentColors.isEmpty()) return@Canvas
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        val topLeft = Offset(inset, inset)
        val sweepPer = 360f / segmentColors.size
        val gap = if (segmentColors.size > 1) SEGMENT_GAP_DEGREES else 0f
        segmentColors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = -90f + index * sweepPer + gap / 2f,
                sweepAngle = (sweepPer - gap).coerceAtLeast(1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Carrusel circular de historias para la parte superior de la seccion de chats.
 * Es un atajo: no sustituye a la pestana de Historias ni a sus tarjetas.
 */
@Composable
fun StoryShortcutRow(
    stories: List<UserStateWithUser>,
    currentUserId: String?,
    currentUserAvatar: String?,
    onNavigateToCreateState: () -> Unit,
    onNavigateToViewState: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(stories) {
        stories.filter { it.state.userId.isNotBlank() }.groupBy { it.state.userId }
    }
    val mine = grouped[currentUserId].orEmpty()
    val others = grouped.filterKeys { it != currentUserId }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        item(key = "story_shortcut_me") {
            StoryShortcutItem(
                label = "Tu historia",
                avatarUrl = currentUserAvatar,
                userId = currentUserId,
                stories = mine,
                showAddBadge = true,
                onClick = {
                    val target = mine.firstOrNull { it.state.viewedByMe != true } ?: mine.firstOrNull()
                    if (target != null) onNavigateToViewState(target.state.id) else onNavigateToCreateState()
                }
            )
        }
        items(others.entries.toList(), key = { it.key }) { entry ->
            val userStories = entry.value
            val profile = userStories.first().profile
            StoryShortcutItem(
                label = profile.displayName,
                avatarUrl = profile.avatarUrl,
                userId = entry.key,
                stories = userStories,
                showAddBadge = false,
                onClick = {
                    val target = userStories.firstOrNull { it.state.viewedByMe != true } ?: userStories.first()
                    onNavigateToViewState(target.state.id)
                }
            )
        }
    }
}

@Composable
private fun StoryShortcutItem(
    label: String?,
    avatarUrl: String?,
    userId: String?,
    stories: List<UserStateWithUser>,
    showAddBadge: Boolean,
    onClick: () -> Unit
) {
    val unseenColor = IosSettingsColors.green
    val seenColor = IosSettingsColors.separator
    val ringColors = remember(stories, unseenColor, seenColor) {
        if (stories.isEmpty()) listOf(seenColor)
        else stories.map { if (it.state.viewedByMe == true) seenColor else unseenColor }
    }
    val displayName = label?.trim()?.takeIf { it.isNotBlank() } ?: ""

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(ItemWidth)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            StorySegmentRing(segmentColors = ringColors)
            PanaAvatar(
                avatarUrl = avatarUrl,
                userId = userId,
                size = AvatarSize,
                borderWidth = 0.dp,
                placeholderName = displayName
            )
            if (showAddBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .background(IosSettingsColors.blue, CircleShape)
                        .border(2.dp, IosSettingsColors.groupBackground, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Crear historia",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Text(
            text = if (displayName.length > 11) displayName.take(11) + "…" else displayName,
            color = IosSettingsColors.secondaryLabel,
            fontFamily = IosFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ItemWidth)
        )
    }
}

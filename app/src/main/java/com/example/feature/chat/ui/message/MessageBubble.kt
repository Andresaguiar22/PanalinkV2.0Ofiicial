package com.example.feature.chat.ui.message

import androidx.compose.runtime.Composable
import com.example.data.model.Message
import com.example.media.playlist.PlaylistSharePayload
import com.example.ui.components.chat.bubble.MessageBubbleEngine
import com.example.ui.components.chat.bubble.MessageGroupPosition
import com.example.util.AudioPlayer
import com.example.util.MessageStatusResolver

@Composable
internal fun MessageBubble(
    message: Message,
    isMe: Boolean,
    myAvatarUrl: String?,
    otherAvatarUrl: String?,
    textSizeSp: Float = 15f,
    allMessages: List<Message>,
    onReply: (Message) -> Unit,
    onDeleteForMe: (String) -> Unit,
    onDeleteForEveryone: (String) -> Unit,
    onForward: (Message) -> Unit,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onImageClick: (String) -> Unit,
    playingAudioUrl: String?,
    isAudioPlaying: Boolean,
    audioProgress: Float,
    audioDurationMs: Int,
    audioCurrentPositionMs: Int,
    audioPlayer: AudioPlayer,
    onAudioPlayStateChange: (String?, Boolean) -> Unit,
    reactions: Map<String, String>?,
    onReact: (String) -> Unit,
    onToggleFavorite: (Message) -> Unit,
    isEdited: Boolean = false,
    onEdit: ((Message) -> Unit)? = null,
    isHighlighted: Boolean = false,
    onGhostOpen: (Message) -> Unit = {},
    onPlaylistAction: (com.example.media.playlist.PlaylistSharePayload, String) -> Unit = { _, _ -> }
) {
    MessageBubbleEngine(
        message = message,
        isMe = isMe,
        groupPosition = MessageGroupPosition.SINGLE,
        deliveryState = com.example.util.MessageStatusResolver.resolveMessageDeliveryState(message, true),
        myAvatarUrl = myAvatarUrl,
        otherAvatarUrl = otherAvatarUrl,
        textSizeSp = textSizeSp,
        allMessages = allMessages,
        onReply = onReply,
        onDeleteForMe = onDeleteForMe,
        onDeleteForEveryone = onDeleteForEveryone,
        onForward = onForward,
        isSelected = isSelected,
        onSelect = onSelect,
        onImageClick = onImageClick,
        playingAudioUrl = playingAudioUrl,
        isAudioPlaying = isAudioPlaying,
        audioProgress = audioProgress,
        audioDurationMs = audioDurationMs,
        audioCurrentPositionMs = audioCurrentPositionMs,
        audioPlayer = audioPlayer,
        onAudioPlayStateChange = onAudioPlayStateChange,
        reactions = reactions,
        onReact = onReact,
        onToggleFavorite = onToggleFavorite,
        isEdited = isEdited,
        onEdit = onEdit,
        isHighlighted = isHighlighted,
        onGhostOpen = onGhostOpen,
        onPlaylistAction = onPlaylistAction
    )
}


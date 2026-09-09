Box(
                 modifier = Modifier
                     .fillMaxWidth()
                     .weight(1f)
                     .heightIn(max = 220.dp)
             ) {
                 VoiceRoomTikTokChat(
                     messages = state.messages,
                     memberById = memberById,
                     onOpenProfile = onOpenProfile,
                     modifier = Modifier.fillMaxSize()
                 )
                 VoiceRoomFloatingEmojiOverlay(
                     emojis = emojiReactions,
                     onDone = { id -> emojiReactions = emojiReactions.filterNot { it.id == id } }
                 )
             }

             VoiceRoomEmojiQuickBar(
                 onReaction = { emoji -> pushReaction(emoji) }
             )

             VoiceRoomInputBar(
                 value = inputText,
                 onValueChange = { inputText = it.take(2000) },
                 onSend = {
                     viewModel.sendMessage(inputText)
                     inputText = ""
                 },
                 isSeated = state.isSeated,
                 isMuted = state.mySeat?.isMuted == true,
                 pendingRequest = state.pendingSeatRequest != null,
                 needsPermission = (!hasMic && state.mySeat?.isMuted != true),
                 onRequestSeat = { viewModel.requestAnySeat() },
                 onToggleMute = { viewModel.toggleMute() },
                 onEnableMic = { permission.launch(Manifest.permission.RECORD_AUDIO) }
             )
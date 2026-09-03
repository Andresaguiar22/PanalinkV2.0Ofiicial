package com.example.live.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.room.track.VideoTrack

@Composable
fun LiveParticipantsLayout(
    broadcasterVideoTrack: VideoTrack?,
    guestVideoTrack: VideoTrack?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LiveVideoSurface(
            videoTrack = broadcasterVideoTrack,
            modifier = Modifier.fillMaxSize()
        )

        if (guestVideoTrack != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 16.dp)
                    .width(120.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LiveVideoSurface(
                        videoTrack = guestVideoTrack,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Co-Host",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

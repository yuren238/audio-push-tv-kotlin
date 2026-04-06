package com.audiopush.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiopush.tv.PlayerState

@Composable
fun MiniPlayer(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.currentEpisode == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = state.currentEpisode.coverUrl,
                contentDescription = "Cover",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.currentEpisode.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp
                )
                Text(
                    text = state.currentEpisode.podcastName,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }

            IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.SkipPrevious,
                    "Previous",
                    tint = if (state.hasPrevious) Color.White else Color.Gray
                )
            }

            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (state.isPlaying) 
                        androidx.compose.material.icons.Icons.Default.Pause 
                    else 
                        androidx.compose.material.icons.Icons.Default.PlayArrow,
                    "Play/Pause",
                    tint = Color.Black
                )
            }

            IconButton(onClick = onNext, enabled = state.hasNext) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.SkipNext,
                    "Next",
                    tint = if (state.hasNext) Color.White else Color.Gray
                )
            }
        }
    }
}

@Composable
fun PlayerControls(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.currentEpisode == null) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = state.currentEpisode.coverUrl,
            contentDescription = "Cover",
            modifier = Modifier.size(280.dp).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = state.currentEpisode.title,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = state.currentEpisode.podcastName,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        var sliderPosition by remember(state.progress) { mutableFloatStateOf(state.progress) }

        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { onSeek(sliderPosition) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(state.position),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = formatDuration(state.duration),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = state.hasPrevious,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.SkipPrevious,
                    "Previous",
                    modifier = Modifier.size(40.dp),
                    tint = if (state.hasPrevious) Color.White else Color.Gray
                )
            }

            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (state.isPlaying) 
                        androidx.compose.material.icons.Icons.Default.Pause 
                    else 
                        androidx.compose.material.icons.Icons.Default.PlayArrow,
                    "Play/Pause",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Black
                )
            }

            IconButton(
                onClick = onNext,
                enabled = state.hasNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.SkipNext,
                    "Next",
                    modifier = Modifier.size(40.dp),
                    tint = if (state.hasNext) Color.White else Color.Gray
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

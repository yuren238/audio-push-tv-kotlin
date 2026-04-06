package com.audiopush.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiopush.tv.PlayerState
import com.audiopush.tv.PlayerState as State
import com.audiopush.tv.ui.components.*

data class TabItem(
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.playerState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    val tabs = listOf(
        TabItem(Icons.Home, "首页"),
        TabItem(Icons.History, "历史"),
        TabItem(Icons.Settings, "设置")
    )

    Scaffold(
        bottomBar = {
            Column {
                // Mini player
                MiniPlayer(
                    state = state,
                    onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                    onNext = { viewModel.playNext() },
                    onPrevious = { viewModel.playPrevious() }
                )

                // Bottom nav
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) },
                            selected = currentTab == index,
                            onClick = { viewModel.setTab(index) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                0 -> HomeTab(
                    state = state,
                    serverUrl = serverUrl,
                    isLoading = isLoading,
                    onPlayPause = { if (state.isPlaying) viewModel.pause() else viewModel.play() },
                    onNext = { viewModel.playNext() },
                    onPrevious = { viewModel.playPrevious() },
                    onSeek = { viewModel.seekTo(it) }
                )
                1 -> HistoryTab(
                    history = state.history,
                    onPlay = { viewModel.playFromHistory(it) },
                    onRemove = { viewModel.removeFromHistory(it) },
                    onClear = { viewModel.clearHistory() }
                )
                2 -> SettingsTab(serverUrl = serverUrl)
            }

            // Error snackbar
            error?.let { errorMsg ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("关闭")
                        }
                    }
                ) {
                    Text(errorMsg)
                }
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun HomeTab(
    state: PlayerState,
    serverUrl: String?,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        item {
            Text(
                text = "AudioPush",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "小宇宙 · 推送到电视",
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // QR Code
        serverUrl?.let { url ->
            item {
                QrCodeView(url = url)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Now playing
        item {
            if (state.currentEpisode != null) {
                PlayerControls(
                    state = state,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.headphones,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无播放内容",
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "扫码推送播客到电视",
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    history: List<com.audiopush.tv.PodcastEpisode>,
    onPlay: (com.audiopush.tv.PodcastEpisode) -> Unit,
    onRemove: (com.audiopush.tv.PodcastEpisode) -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "播放历史",
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("清空")
                }
            }
        }

        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.history,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无播放历史",
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            LazyColumn {
                items(history, key = { it.id }) { episode ->
                    HistoryItem(
                        episode = episode,
                        onPlay = { onPlay(episode) },
                        onRemove = { onRemove(episode) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    episode: com.audiopush.tv.PodcastEpisode,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.coverUrl,
            contentDescription = "Cover",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = episode.podcastName,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.delete_outline,
                "Delete",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun SettingsTab(serverUrl: String?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "设置",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Server status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (serverUrl != null) Icons.wifi else Icons.wifi_off,
                        "Status",
                        tint = if (serverUrl != null) Color.Green else Color.Red
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "推送服务",
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (serverUrl != null) "运行中: $serverUrl" else "未运行",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App info
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AudioPush TV",
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "版本 1.0.0",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "小宇宙播客推送播放器，支持手机扫码推送音频到电视播放。",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 14.sp
                )
            }
        }
    }
}

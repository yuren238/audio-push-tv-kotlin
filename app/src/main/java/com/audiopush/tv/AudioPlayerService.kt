package com.audiopush.tv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioPlayerService(context: Context) {

    private val player = ExoPlayer.Builder(context).build()
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var playlist: List<PodcastEpisode> = emptyList()
    private var currentIndex: Int = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    fun playEpisode(episode: PodcastEpisode) {
        val mediaItem = MediaItem.fromUri(episode.audioUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        updateState { it.copy(currentEpisode = episode) }
    }

    fun playPlaylist(episodes: List<PodcastEpisode>, startIndex: Int = 0) {
        if (episodes.isEmpty()) return
        playlist = episodes
        currentIndex = startIndex
        playEpisode(episodes[startIndex])
        updateState { it.copy(playlist = episodes, currentIndex = startIndex) }
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun playNext() {
        if (currentIndex < playlist.size - 1) {
            currentIndex++
            playEpisode(playlist[currentIndex])
            updateState { it.copy(currentIndex = currentIndex) }
        }
    }

    fun playPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            playEpisode(playlist[currentIndex])
            updateState { it.copy(currentIndex = currentIndex) }
        }
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration.coerceAtLeast(0)

    fun release() {
        player.release()
    }

    private fun updateState(update: (PlayerState) -> PlayerState) {
        _playerState.value = update(_playerState.value)
    }

    fun addToHistory(episode: PodcastEpisode) {
        updateState {
            it.copy(history = (listOf(episode) + it.history).distinctBy { e -> e.id }.take(100))
        }
    }

    fun playFromHistory(episode: PodcastEpisode) {
        val index = _playerState.value.history.indexOf(episode)
        if (index >= 0) {
            playEpisode(episode)
        } else {
            playEpisode(episode)
        }
    }

    fun removeFromHistory(episode: PodcastEpisode) {
        updateState {
            it.copy(history = it.history.filter { e -> e.id != episode.id })
        }
    }
}

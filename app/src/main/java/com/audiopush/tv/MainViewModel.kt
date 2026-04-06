package com.audiopush.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioPlayer = AudioPlayerService(application)
    private val parser = PodcastParser()

    private var pairingServer: PairingServer? = null
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentTab = MutableStateFlow(0)
    val currentTab: StateFlow<Int> = _currentTab.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    init {
        startPairingServer()
        observePlayerState()
    }

    private fun startPairingServer() {
        pairingServer = PairingServer { url ->
            viewModelScope.launch {
                playUrl(url)
            }
        }
        pairingServer?.start()
        _serverUrl.value = pairingServer?.serverUrl
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            audioPlayer.playerState.collect { state ->
                _playerState.value = state
            }
        }
        startProgressUpdater()
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val position = audioPlayer.getCurrentPosition()
                val duration = audioPlayer.getDuration()
                _playerState.value = _playerState.value.copy(
                    position = position,
                    duration = duration,
                    progress = if (duration > 0) position.toFloat() / duration else 0f
                )
            }
        }
    }

    fun playUrl(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            parser.parseUrl(url).fold(
                onSuccess = { episode ->
                    audioPlayer.addToHistory(episode)
                    audioPlayer.playEpisode(episode)
                },
                onFailure = { e ->
                    _error.value = e.message ?: "解析失败"
                }
            )

            _isLoading.value = false
        }
    }

    fun play() = audioPlayer.play()
    fun pause() = audioPlayer.pause()
    fun seekTo(progress: Float) {
        val position = (progress * audioPlayer.getDuration()).toLong()
        audioPlayer.seekTo(position)
    }

    fun playNext() {
        audioPlayer.playNext()
    }

    fun playPrevious() {
        audioPlayer.playPrevious()
    }

    fun setVolume(volume: Float) {
        audioPlayer.setVolume(volume)
    }

    fun playFromHistory(episode: PodcastEpisode) {
        audioPlayer.playFromHistory(episode)
    }

    fun removeFromHistory(episode: PodcastEpisode) {
        audioPlayer.removeFromHistory(episode)
    }

    fun clearHistory() {
        _playerState.value = _playerState.value.copy(history = emptyList())
    }

    fun setTab(index: Int) {
        _currentTab.value = index
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        pairingServer?.stop()
        audioPlayer.release()
    }
}

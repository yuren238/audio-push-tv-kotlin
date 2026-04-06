package com.audiopush.tv

import java.util.Date

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val coverUrl: String?,
    val podcastName: String,
    val duration: Long = 0,
    val publishDate: Date? = null
)

data class PlayerState(
    val currentEpisode: PodcastEpisode? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val duration: Long = 0,
    val position: Long = 0,
    val playlist: List<PodcastEpisode> = emptyList(),
    val currentIndex: Int = 0,
    val history: List<PodcastEpisode> = emptyList()
) {
    val hasNext: Boolean get() = currentIndex < playlist.size - 1
    val hasPrevious: Boolean get() = currentIndex > 0
}

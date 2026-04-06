package com.audiopush.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Date

class PodcastParser {

    suspend fun parseUrl(url: String): Result<PodcastEpisode> = withContext(Dispatchers.IO) {
        try {
            when {
                url.contains("/episode/") -> parseEpisode(url)
                url.contains("/podcast/") -> parsePodcast(url)
                else -> Result.failure(Exception("不支持的链接格式"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseEpisode(url: String): Result<PodcastEpisode> {
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15")
            .header("Accept", "text/html,application/xhtml+xml")
            .timeout(15000)
            .get()

        val title = extractTitle(response)
        val audioUrl = extractAudioUrl(response)
        val coverUrl = extractCoverUrl(response)
        val podcastName = extractPodcastName(response)

        return if (audioUrl != null) {
            Result.success(
                PodcastEpisode(
                    id = generateId(url),
                    title = title,
                    audioUrl = audioUrl,
                    coverUrl = coverUrl,
                    podcastName = podcastName
                )
            )
        } else {
            Result.failure(Exception("无法解析音频地址"))
        }
    }

    private fun parsePodcast(url: String): Result<PodcastEpisode> {
        val response = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15")
            .timeout(15000)
            .get()

        val title = extractTitle(response)
        val audioUrl = extractAudioUrl(response)
        val coverUrl = extractCoverUrl(response)
        val podcastName = extractPodcastName(response)

        return if (audioUrl != null) {
            Result.success(
                PodcastEpisode(
                    id = generateId(url),
                    title = title,
                    audioUrl = audioUrl,
                    coverUrl = coverUrl,
                    podcastName = podcastName
                )
            )
        } else {
            // 尝试从页面脚本中提取最新单集
            val episodes = extractEpisodesFromScript(response)
            if (episodes.isNotEmpty()) {
                val episode = episodes.first()
                Result.success(episode.copy(id = generateId(url)))
            } else {
                Result.failure(Exception("无法解析音频地址"))
            }
        }
    }

    private fun extractTitle(response: org.jsoup.nodes.Document): String {
        // 尝试 JSON-LD
        val jsonLd = response.select("script[type=application/ld+json]")
        for (script in jsonLd) {
            try {
                val json = JSONObject(script.data())
                val type = json.optString("@type")
                if (type == "PodcastEpisode" || type == "AudioObject") {
                    return json.optString("name", json.optString("headline", ""))
                }
            } catch (e: Exception) {}
        }

        // 尝试 og:title
        val ogTitle = response.selectFirst("meta[property=og:title]")
        return ogTitle?.attr("content") ?: response.title()
    }

    private fun extractAudioUrl(response: org.jsoup.nodes.Document): String? {
        // 尝试 JSON-LD
        val jsonLd = response.select("script[type=application/ld+json]")
        for (script in jsonLd) {
            try {
                val json = JSONObject(script.data())
                val audio = json.optJSONObject("audio") ?: json.optString("audio")
                if (audio is String && audio.contains(".mp3")) return audio
                if (audio is JSONObject) {
                    val url = audio.optString("url", "")
                    if (url.contains(".mp3")) return url
                }
            } catch (e: Exception) {}
        }

        // 尝试 audio 标签
        val audioElement = response.selectFirst("audio source[src]")
        audioElement?.attr("src")?.let { if (it.contains(".mp3")) return it }

        // 尝试 meta og:audio
        val ogAudio = response.selectFirst("meta[property=og:audio]")
        ogAudio?.attr("content")?.let { if (it.contains(".mp3")) return it }

        return null
    }

    private fun extractCoverUrl(response: org.jsoup.nodes.Document): String? {
        // 尝试 JSON-LD
        val jsonLd = response.select("script[type=application/ld+json]")
        for (script in jsonLd) {
            try {
                val json = JSONObject(script.data())
                val image = json.opt("image")
                if (image is String) return image
                if (image is JSONObject) return image.optString("url")
                if (image is JSONArray && image.length() > 0) {
                    val first = image.get(0)
                    if (first is String) return first
                    if (first is JSONObject) return first.optString("url")
                }
            } catch (e: Exception) {}
        }

        // 尝试 og:image
        val ogImage = response.selectFirst("meta[property=og:image]")
        return ogImage?.attr("content")
    }

    private fun extractPodcastName(response: org.jsoup.nodes.Document): String {
        // 尝试 JSON-LD
        val jsonLd = response.select("script[type=application/ld+json]")
        for (script in jsonLd) {
            try {
                val json = JSONObject(script.data())
                val partOf = json.optJSONObject("partOfSeries")
                if (partOf != null) {
                    return partOf.optString("name", "")
                }
            } catch (e: Exception) {}
        }

        // 尝试 og:site_name
        val ogSiteName = response.selectFirst("meta[property=og:site_name]")
        return ogSiteName?.attr("content") ?: "小宇宙播客"
    }

    private fun extractEpisodesFromScript(response: org.jsoup.nodes.Document): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()

        // 查找页面中的 episodes 数据
        val scripts = response.select("script")
        for (script in scripts) {
            val data = script.data()
            if (data.contains("episodes") || data.contains("trackData")) {
                try {
                    // 尝试提取 JSON 数据
                    val jsonPattern = """\["episode"\]\s*=\s*(\{.*?\});""".toRegex(RegexOption.DOT_MATCHES_ALL)
                    jsonPattern.find(data)?.let { match ->
                        val json = JSONObject(match.groupValues[1])
                        val tracks = json.optJSONArray("tracks") ?: json.optJSONArray("episodes")
                        tracks?.let {
                            for (i in 0 until minOf(it.length(), 5)) {
                                val track = it.getJSONObject(i)
                                val audioUrl = track.optString("audio", "")
                                if (audioUrl.contains(".mp3")) {
                                    episodes.add(
                                        PodcastEpisode(
                                            id = track.optString("id", generateId(track.optString("url", ""))),
                                            title = track.optString("title", track.optString("name", "")),
                                            audioUrl = audioUrl,
                                            coverUrl = track.optString("coverUrl", track.optString("image", "")),
                                            podcastName = track.optString("podcastName", track.optString("album", ""))
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
        }

        return episodes
    }

    private fun generateId(url: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

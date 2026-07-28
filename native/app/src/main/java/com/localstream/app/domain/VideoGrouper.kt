package com.localstream.app.domain

import com.localstream.app.domain.model.MovieCollection
import com.localstream.app.domain.model.VideoItem

object VideoGrouper {
    fun groupVideos(
        videos: List<VideoItem>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>,
        whitelistedVideos: Set<String>
    ): List<VideoItem> {
        val filteredVideos = videos.filter { video ->
            whitelistedVideos.contains(video.name) || !Formatters.isPersonalVideo(video.name, video.path)
        }

        val (seriesMap, standaloneList) = partitionSeriesAndStandalone(filteredVideos)
        val seriesResult = formatSeriesGroups(seriesMap)
        val (sagaGroups, remainingStandalone) = groupMovieSagas(standaloneList, movieCollections, releaseDates)

        return seriesResult + sagaGroups + remainingStandalone
    }

    private fun partitionSeriesAndStandalone(
        videos: List<VideoItem>
    ): Pair<Map<String, List<VideoItem>>, List<VideoItem>> {
        val groups = mutableMapOf<String, MutableList<VideoItem>>()
        val standalone = mutableListOf<VideoItem>()

        videos.forEach { video ->
            val match = VideoNameParser.SEASON_EPISODE_REGEX.find(video.name)
            if (match != null || !video.seriesName.isNullOrEmpty()) {
                val updatedVideo = extractSeriesFields(video, match)
                val finalName = resolveSeriesName(updatedVideo, match)
                groups.getOrPut(finalName) { mutableListOf() }.add(updatedVideo)
            } else {
                standalone.add(video.copy(cleanTitle = TitleCleaner.getCleanTitle(video.name)))
            }
        }
        return Pair(groups, standalone)
    }

    private fun extractSeriesFields(video: VideoItem, match: MatchResult?): VideoItem {
        if (match == null) return video
        val groupValues = match.groupValues
        val sStr = if (groupValues[1].isNotEmpty()) groupValues[1] else groupValues[4]
        val eStr = if (groupValues[3].isNotEmpty()) groupValues[3] else groupValues[6]
        return video.copy(season = sStr.toIntOrNull(), episode = eStr.toIntOrNull())
    }

    private fun resolveSeriesName(video: VideoItem, match: MatchResult?): String {
        val sName = video.seriesName ?: if (match != null) {
            video.name.substring(0, match.range.first)
                .replace(Regex("[\\.\\-_/\\\\\\[\\]\\(\\)]"), " ")
                .trim()
                .replace(Regex("[\\s\\-]+$"), "")
        } else {
            TitleCleaner.getCleanTitle(video.name)
        }
        return if (sName.isEmpty()) "Série Inconnue" else sName
    }

    private fun formatSeriesGroups(groups: Map<String, List<VideoItem>>): List<VideoItem> {
        return groups.map { (seriesName, epList) ->
            val sorted = epList.sortedWith { a, b ->
                val sComp = (a.season ?: 0).compareTo(b.season ?: 0)
                if (sComp != 0) sComp else (a.episode ?: 0).compareTo(b.episode ?: 0)
            }
            val first = sorted.first()
            VideoItem(
                url = first.url,
                name = seriesName,
                type = "series",
                path = first.path,
                isSeriesGroup = true,
                isTvSeries = true,
                episodes = sorted,
                seriesName = seriesName
            )
        }
    }

    private fun groupMovieSagas(
        standaloneVideos: List<VideoItem>,
        movieCollections: Map<String, MovieCollection>,
        releaseDates: Map<String, String>
    ): Pair<List<VideoItem>, List<VideoItem>> {
        val colMap = mutableMapOf<String, MutableList<VideoItem>>()
        val colNames = mutableMapOf<String, String>()
        val usedNames = mutableSetOf<String>()

        standaloneVideos.forEach { v ->
            val col = movieCollections[v.name]
            if (col != null) {
                val key = "col_${col.id}"
                colNames[key] = col.name
                colMap.getOrPut(key) { mutableListOf() }.add(v)
                usedNames.add(v.name)
            }
        }

        val sagas = mutableListOf<VideoItem>()
        colMap.forEach { (key, films) ->
            val colName = colNames[key] ?: ""
            if (films.size > 1) {
                val sorted = films.sortedWith { a, b ->
                    val dA = releaseDates[a.name] ?: ""
                    val dB = releaseDates[b.name] ?: ""
                    val dComp = dA.compareTo(dB)
                    if (dComp != 0) dComp else a.name.compareTo(b.name)
                }
                val first = sorted.first()
                sagas.add(
                    first.copy(
                        name = colName,
                        type = "series",
                        isSeriesGroup = true,
                        isTvSeries = false,
                        episodes = sorted,
                        seriesName = colName
                    )
                )
            } else {
                films.forEach { usedNames.remove(it.name) }
            }
        }

        val remaining = standaloneVideos.filter { !usedNames.contains(it.name) }
        return Pair(sagas, remaining)
    }
}


package com.localstream.app.domain.model

enum class SortBy {
    ALPHA, DATE, SIZE, DURATION
}

enum class ResolutionFilter {
    ALL, FOUR_K, TWO_K, ONE_THOUSAND_EIGHTY_P, SEVEN_HUNDRED_TWENTY_P, SD
}

data class FilterSortOptions(
    val sortBy: SortBy = SortBy.ALPHA,
    val filterGenre: Int? = null,
    val filterResolution: ResolutionFilter = ResolutionFilter.ALL,
    val releaseDates: Map<String, String> = emptyMap(),
    val videoGenres: Map<String, List<Int>> = emptyMap(),
    val videoDurations: Map<String, Long> = emptyMap(),
    val watchedVideos: Map<String, Boolean> = emptyMap(),
)


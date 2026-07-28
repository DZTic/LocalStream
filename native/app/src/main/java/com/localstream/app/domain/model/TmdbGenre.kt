package com.localstream.app.domain.model

enum class TmdbGenre(val id: Int, val genreName: String) {
    ACTION(28, "Action"),
    ADVENTURE(12, "Aventure"),
    ANIMATION(16, "Animation"),
    COMEDY(35, "Comédie"),
    CRIME(80, "Crime"),
    DOCUMENTARY(99, "Documentaire"),
    DRAMA(18, "Drame"),
    FAMILY(10751, "Familial"),
    FANTASY(14, "Fantastique"),
    HISTORY(36, "Histoire"),
    HORROR(27, "Horreur"),
    MUSIC(10402, "Musique"),
    MYSTERY(9648, "Mystère"),
    ROMANCE(10749, "Romance"),
    SCIENCE_FICTION(878, "Science-Fiction"),
    TV_MOVIE(10770, "Téléfilm"),
    THRILLER(53, "Thriller"),
    WAR(10752, "Guerre"),
    WESTERN(37, "Western"),
    ACTION_ADVENTURE(10759, "Action & Adventure"),
    KIDS(10762, "Kids"),
    NEWS(10763, "News"),
    REALITY(10764, "Reality"),
    SCI_FI_FANTASY(10765, "Sci-Fi & Fantasy"),
    SOAP(10766, "Soap"),
    TALK(10767, "Talk"),
    WAR_POLITICS(10768, "War & Politics");

    companion object {
        private val mapById = entries.associateBy { it.id }
        fun fromId(id: Int): TmdbGenre? = mapById[id]
        fun getGenreName(id: Int): String? = mapById[id]?.genreName
    }
}


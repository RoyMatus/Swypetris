package ru.itoltec.swypetris

/** Stable IDs and names for the eight full tracks. */
enum class Song(val id: String, val title: String, val resource: Int) {
    KOROBEINIKI("korobeiniki", "Коробейники", R.raw.korobeiniki), KALINKA("kalinka", "Калинка", R.raw.kalinka),
    KAMARINSKAYA("kamarinskaya", "Камаринская", R.raw.kamarinskaya), BARYNYA("barynya", "Барыня", R.raw.barynya),
    SVETIT("svetit_mesyat", "Светит месяц", R.raw.svetit_mesyat), VO_SADU("vo_sadu", "Во саду ли, в огороде", R.raw.vo_sadu),
    TREPAK("trepak", "Трепак", R.raw.trepak), SUGAR_PLUM("sugar_plum", "Танец Феи Драже", R.raw.sugar_plum)
}

/** Persisted music selection, independent of a temporary player pause. */
sealed class MusicSelection(val id: String, val title: String) {
    /** Disables music playback without affecting game sound effects. */
    data object Off : MusicSelection("off", "Выключена")
    /** Plays all bundled tracks in rounds with randomized order. */
    data object ShuffleAll : MusicSelection("shuffle", "Все песни — случайный порядок")
    /** Plays one selected [song] instead of the full shuffled playlist. */
    data class Track(val song: Song) : MusicSelection(song.id, song.title)

    companion object {
        val all: List<MusicSelection> get() = listOf(Off, ShuffleAll) + Song.entries.map(::Track)
        /** Restores the new setting or preserves the meaning of the old on/off flag. */
        fun restore(id: String?, legacyEnabled: Boolean): MusicSelection =
            all.firstOrNull { it.id == id } ?: if (legacyEnabled) ShuffleAll else Off
    }
}

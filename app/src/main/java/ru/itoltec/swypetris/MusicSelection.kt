package ru.itoltec.swypetris

/** Постоянные идентификаторы и названия восьми полных композиций. */
enum class Song(val id: String, val title: String, val resource: Int) {
    KOROBEINIKI("korobeiniki", "Коробейники", R.raw.korobeiniki), KALINKA("kalinka", "Калинка", R.raw.kalinka),
    KAMARINSKAYA("kamarinskaya", "Камаринская", R.raw.kamarinskaya), BARYNYA("barynya", "Барыня", R.raw.barynya),
    SVETIT("svetit_mesyat", "Светит месяц", R.raw.svetit_mesyat), VO_SADU("vo_sadu", "Во саду ли, в огороде", R.raw.vo_sadu),
    TREPAK("trepak", "Трепак", R.raw.trepak), SUGAR_PLUM("sugar_plum", "Танец Феи Драже", R.raw.sugar_plum)
}

/** Сохраняемый выбор музыки, независимый от временной паузы проигрывателя. */
sealed class MusicSelection(val id: String, val title: String) {
    data object Off : MusicSelection("off", "Выключена")
    data object ShuffleAll : MusicSelection("shuffle", "Все песни — случайный порядок")
    data class Track(val song: Song) : MusicSelection(song.id, song.title)

    companion object {
        val all: List<MusicSelection> get() = listOf(Off, ShuffleAll) + Song.entries.map(::Track)
        /** Читает новую настройку; при её отсутствии сохраняет смысл прежней галки. */
        fun restore(id: String?, legacyEnabled: Boolean): MusicSelection =
            all.firstOrNull { it.id == id } ?: if (legacyEnabled) ShuffleAll else Off
    }
}

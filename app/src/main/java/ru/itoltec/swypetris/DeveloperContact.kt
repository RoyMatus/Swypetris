package ru.itoltec.swypetris

import android.content.Intent
import android.net.Uri

/** Developer contact with a display address, copyable text, and an external URI. */
internal enum class DeveloperContact(val title: String, val address: String, val uri: String) {
    EMAIL("Email", "piligrim18@gmail.com", "mailto:piligrim18@gmail.com"),
    TELEGRAM("Telegram", "@RoyMatus", "https://t.me/RoyMatus"),
    WEBSITE("Сайт", "https://itoltec.ru/", "https://itoltec.ru/");

    /** Builds an email or browser intent without sending any message on the user's behalf. */
    fun intent(): Intent = Intent(if (this == EMAIL) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(uri))
}

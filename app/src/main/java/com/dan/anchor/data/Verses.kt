package com.dan.anchor.data

import android.content.Context
import org.json.JSONArray

data class BlockVerse(
    val reference: String,
    val book: String,
    val chapter: Int,
    val verseStart: Int,
    val verseEnd: Int,
    val theme: String,
    val text: String
)

/**
 * The verses that show up when something is blocked. All 45 were pulled straight
 * out of the bundled Bible database, so the wording matches what you'd get by
 * opening the passage in the reader.
 */
object Verses {

    @Volatile private var cache: List<BlockVerse>? = null

    fun all(context: Context): List<BlockVerse> {
        cache?.let { return it }
        val json = context.assets.open("verses.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        val out = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BlockVerse(
                reference = o.getString("reference"),
                book = o.getString("book"),
                chapter = o.getInt("chapter"),
                verseStart = o.getInt("verseStart"),
                verseEnd = o.getInt("verseEnd"),
                theme = o.optString("theme", ""),
                text = o.getString("text")
            )
        }
        cache = out
        return out
    }

    /** Steps through the list rather than picking at random, so you don't get the same one twice running. */
    fun next(context: Context): BlockVerse {
        val list = all(context)
        val prefs = Prefs.get(context)
        val i = (prefs.lastVerseIndex + 1).let { if (it >= list.size) 0 else it }
        prefs.lastVerseIndex = i
        return list[i]
    }
}

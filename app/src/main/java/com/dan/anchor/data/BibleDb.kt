package com.dan.anchor.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

data class Book(val id: Int, val code: String, val name: String, val testament: String, val chapters: Int)
data class Verse(val number: Int, val text: String)
data class SearchHit(val bookId: Int, val bookName: String, val chapter: Int, val verse: Int, val text: String)

/**
 * The whole World English Bible ships inside the APK as a SQLite file, copied
 * out of assets once on first launch. After that everything — reading, search,
 * and the verses on the block screen — works with no network at all.
 */
object BibleDb {

    private const val ASSET = "bible.db"
    private const val FILE = "bible.db"

    @Volatile private var db: SQLiteDatabase? = null
    @Volatile private var bookCache: List<Book>? = null

    fun open(context: Context): SQLiteDatabase {
        db?.let { if (it.isOpen) return it }
        synchronized(this) {
            db?.let { if (it.isOpen) return it }
            val target = File(context.filesDir, FILE)
            if (!target.exists() || target.length() < 1_000_000) {
                unpack(context, target)
            }
            return try {
                openAt(target)
            } catch (first: Exception) {
                // A half-written or corrupted copy is recoverable — throw it
                // away and unpack again rather than failing forever.
                runCatching { target.delete() }
                unpack(context, target)
                openAt(target)
            }
        }
    }

    private fun openAt(target: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            target.absolutePath, null, SQLiteDatabase.OPEN_READONLY
        ).also { db = it }

    private fun unpack(context: Context, target: File) {
        val tmp = File(target.parentFile, "$FILE.tmp")
        try {
            context.assets.open(ASSET).use { input ->
                tmp.outputStream().buffered(64 * 1024).use { out -> input.copyTo(out) }
            }
        } catch (e: java.io.FileNotFoundException) {
            throw IllegalStateException(
                "bible.db is missing from the build. Check that app/src/main/assets/bible.db " +
                    "exists and is about 12 MB, then do Build > Clean Project.",
                e
            )
        }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun books(context: Context): List<Book> {
        bookCache?.let { return it }
        val out = mutableListOf<Book>()
        open(context).rawQuery(
            "SELECT id, code, name, testament, chapters FROM books ORDER BY id", null
        ).use { c ->
            while (c.moveToNext()) {
                out += Book(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4))
            }
        }
        bookCache = out
        return out
    }

    fun book(context: Context, id: Int): Book? = books(context).firstOrNull { it.id == id }

    fun bookByName(context: Context, name: String): Book? =
        books(context).firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun chapter(context: Context, bookId: Int, chapter: Int): List<Verse> {
        val out = mutableListOf<Verse>()
        open(context).rawQuery(
            "SELECT verse, text FROM verses WHERE book_id=? AND chapter=? ORDER BY verse",
            arrayOf(bookId.toString(), chapter.toString())
        ).use { c ->
            while (c.moveToNext()) out += Verse(c.getInt(0), c.getString(1))
        }
        return out
    }

    fun passage(context: Context, bookName: String, chapter: Int, from: Int, to: Int): String {
        val b = bookByName(context, bookName) ?: return ""
        val sb = StringBuilder()
        open(context).rawQuery(
            "SELECT text FROM verses WHERE book_id=? AND chapter=? AND verse BETWEEN ? AND ? ORDER BY verse",
            arrayOf(b.id.toString(), chapter.toString(), from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(c.getString(0))
            }
        }
        return sb.toString()
    }

    fun search(context: Context, query: String, limit: Int = 120): List<SearchHit> {
        val raw = query.trim()
        if (raw.length < 2) return emptyList()

        // Full-text search matches whole words, so half-typed words find nothing
        // as you go. Marking the last word as a prefix makes results appear while
        // you're still typing. Punctuation is stripped because it's syntax to FTS.
        val words = raw.lowercase()
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val q = words.dropLast(1).joinToString(" ") { it } +
            (if (words.size > 1) " " else "") + words.last() + "*"
        val out = mutableListOf<SearchHit>()
        // FTS4 gives fast whole-Bible search; fall back to LIKE if the query
        // has characters the tokeniser chokes on.
        val sql = """
            SELECT v.book_id, b.name, v.chapter, v.verse, v.text
            FROM verses_fts f
            JOIN verses v ON v.rowid = f.rowid
            JOIN books b ON b.id = v.book_id
            WHERE verses_fts MATCH ?
            LIMIT ?
        """.trimIndent()
        val database = open(context)
        val cursor = runCatching {
            database.rawQuery(sql, arrayOf(q, limit.toString()))
        }.getOrElse {
            database.rawQuery(
                """
                SELECT v.book_id, b.name, v.chapter, v.verse, v.text
                FROM verses v JOIN books b ON b.id = v.book_id
                WHERE v.text LIKE ? LIMIT ?
                """.trimIndent(),
                arrayOf("%$raw%", limit.toString())
            )
        }
        cursor.use { c ->
            while (c.moveToNext()) {
                out += SearchHit(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3), c.getString(4))
            }
        }
        return out
    }
}

package com.dan.anchor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.anchor.data.BibleDb
import com.dan.anchor.data.Book
import com.dan.anchor.data.Prefs
import com.dan.anchor.data.SearchHit
import com.dan.anchor.data.Verse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Pane { READING, BOOKS, SEARCH }

/**
 * The reader. All 31,098 verses live on the phone, so this works in a tunnel,
 * on a plane, or with the data turned off.
 */
/** Where the block screen wants the reader to open. */
data class PassageTarget(val book: String, val chapter: Int, val verse: Int)

@Composable
fun ReadScreen(
    target: PassageTarget? = null,
    onTargetHandled: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs.get(ctx) }
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var bookId by remember { mutableIntStateOf(prefs.lastRead.substringBefore(':').toIntOrNull() ?: 43) }
    var chapter by remember { mutableIntStateOf(prefs.lastRead.substringAfter(':').toIntOrNull() ?: 1) }
    var verses by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var pane by remember { mutableStateOf(Pane.READING) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var pendingVerse by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { BibleDb.books(ctx) } }
            .onSuccess { books = it; error = null }
            .onRealFailure { error = describe(it) }
        loading = false
    }

    // Jumped here from a block screen: open that passage and scroll to the verse.
    LaunchedEffect(target, books) {
        val t = target ?: return@LaunchedEffect
        if (books.isEmpty()) return@LaunchedEffect
        val b = books.firstOrNull { it.name.equals(t.book, ignoreCase = true) }
        if (b != null) {
            pane = Pane.READING
            bookId = b.id
            chapter = t.chapter.coerceIn(1, b.chapters)
            pendingVerse = t.verse
        }
        onTargetHandled()
    }

    LaunchedEffect(bookId, chapter) {
        runCatching { withContext(Dispatchers.IO) { BibleDb.chapter(ctx, bookId, chapter) } }
            .onSuccess {
                verses = it
                error = null
                prefs.lastRead = "$bookId:$chapter"
                val want = pendingVerse
                runCatching {
                    if (want != null) {
                        // Land a couple of verses early so the one you came for
                        // has its context above it rather than jammed to the top.
                        val idx = it.indexOfFirst { v -> v.number == want }
                        listState.scrollToItem(if (idx > 2) idx - 2 else 0)
                    } else {
                        listState.scrollToItem(0)
                    }
                }
                pendingVerse = null
            }
            .onRealFailure { error = describe(it) }
    }

    LaunchedEffect(query) {
        if (query.length >= 2) {
            runCatching { withContext(Dispatchers.IO) { BibleDb.search(ctx, query) } }
                .onSuccess { hits = it; error = null }
                .onRealFailure { error = describe(it) }
        } else hits = emptyList()
    }

    val book = books.firstOrNull { it.id == bookId }

    Column(Modifier.fillMaxSize()) {

        // header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pane != Pane.READING) {
                Icon(
                    Icons.Outlined.ArrowBack, contentDescription = "Back to reading",
                    tint = Ink.Slate,
                    modifier = Modifier.size(20.dp).clickable { pane = Pane.READING; query = "" }
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when (pane) {
                        Pane.READING -> "WORLD ENGLISH BIBLE"
                        Pane.BOOKS -> "CHOOSE A BOOK"
                        Pane.SEARCH -> "SEARCH"
                    },
                    style = Eyebrow, color = Ink.Dim
                )
                if (pane == Pane.READING) {
                    Spacer(Modifier.height(8.dp))
                    // Looks like a control, because it is one — plain text here
                    // gave no hint that tapping opens the book list.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Ink.Surface)
                            .border(1.dp, Ink.Hairline, RoundedCornerShape(10.dp))
                            .clickable { pane = Pane.BOOKS }
                            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${book?.name ?: ""} $chapter",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.ExpandMore,
                            contentDescription = "Choose a book",
                            tint = Ink.Brass,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (pane == Pane.READING) {
                Icon(
                    Icons.Outlined.Search, contentDescription = "Search the Bible",
                    tint = Ink.Slate,
                    modifier = Modifier.size(20.dp).clickable { pane = Pane.SEARCH }
                )
            }
        }

        if (error != null) {
            LoadFailure(error!!) { error = null; bookId = bookId; chapter = chapter }
            return@Column
        }

        if (loading && books.isEmpty()) {
            Text(
                "Unpacking the Bible\u2026 this only happens once.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.Dim,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            return@Column
        }

        when (pane) {
            Pane.READING -> {
                book?.let { b ->
                    ChapterStrip(b.chapters, chapter) { chapter = it }
                }
                // Compose text can't be selected unless you ask for it. Without
                // this, long-pressing a verse does nothing at all.
                SelectionContainer(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 48.dp)
                ) {
                    items(verses, key = { it.number }) { v ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Ink.Brass, fontSize = 12.sp)) {
                                    append("${v.number}  ")
                                }
                                append(v.text)
                            },
                            style = Scripture,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    item {
                        book?.let { b ->
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (chapter > 1) {
                                    NavChip("Previous", Modifier.weight(1f)) { chapter -= 1 }
                                }
                                if (chapter < b.chapters) {
                                    NavChip("Next", Modifier.weight(1f)) { chapter += 1 }
                                }
                            }
                        }
                    }
                }
                }
            }

            Pane.BOOKS -> {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    listOf("OT" to "OLD TESTAMENT", "NT" to "NEW TESTAMENT").forEach { (code, title) ->
                        item(key = "h$code") {
                            Spacer(Modifier.height(16.dp))
                            Text(title, style = Eyebrow, color = Ink.Dim)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(books.filter { it.testament == code }, key = { it.id }) { b ->
                            Text(
                                b.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (b.id == bookId) Ink.Brass else Ink.Bone,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        bookId = b.id; chapter = 1; pane = Pane.READING
                                    }
                                    .padding(vertical = 13.dp)
                            )
                        }
                    }
                }
            }

            Pane.SEARCH -> {
                Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                    Field(query, { query = it }, "A word or phrase")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (query.length < 2) "Searches all 31,098 verses."
                        else "${hits.size} result${if (hits.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium, color = Ink.Dim
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn {
                        items(hits, key = { "${it.bookId}-${it.chapter}-${it.verse}" }) { hit ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            bookId = hit.bookId; chapter = hit.chapter
                                            pane = Pane.READING; query = ""
                                        }
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text(
                                    "${hit.bookName} ${hit.chapter}:${hit.verse}".uppercase(),
                                    style = Eyebrow, color = Ink.Brass
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(hit.text, style = Scripture.copy(fontSize = 16.sp, lineHeight = 26.sp))
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Hairline))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The reader used to take the whole app down if anything went wrong opening the
 * database. Now it says what happened, which is both survivable and diagnosable.
 */
@Composable
private fun LoadFailure(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text("COULDN'T OPEN THE BIBLE", style = Eyebrow, color = Ink.Rust)
        Spacer(Modifier.height(14.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "RETRY",
            style = Eyebrow,
            color = Ink.Brass,
            modifier = Modifier.clickable(onClick = onRetry)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Blocking is unaffected \u2014 it doesn't use the Bible database.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.Dim
        )
    }
}

/**
 * Cancellation isn't a failure — Compose throws it every time you type another
 * letter and the previous search is dropped. Rethrowing keeps the coroutine
 * machinery working instead of painting a routine event as a broken database.
 */
private inline fun <T> Result<T>.onRealFailure(action: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let { t ->
        if (t is CancellationException) throw t
        action(t)
    }
    return this
}

private fun describe(t: Throwable): String {
    val name = t::class.java.simpleName
    val msg = t.message?.take(300).orEmpty()
    return if (msg.isBlank()) name else "$name: $msg"
}

@Composable
private fun ChapterStrip(total: Int, current: Int, onPick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items((1..total).toList()) { n ->
            val on = n == current
            Text(
                "$n",
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) Ink.Void else Ink.Slate,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) Ink.Brass else Ink.Surface)
                    .clickable { onPick(n) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun NavChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink.Surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label.uppercase(), style = Eyebrow, color = Ink.Bone)
    }
}

package com.dan.anchor

import android.app.Application
import com.dan.anchor.data.BibleDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnchorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Unpacking 12 MB of Bible takes a second or two the very first time.
        // Do it off the main thread so the app never stalls on launch.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { BibleDb.books(this@AnchorApp) }
        }
    }
}

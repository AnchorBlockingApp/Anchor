package com.dan.anchor.block

/**
 * A window into what the service is actually doing.
 *
 * URL reading depends on browser internals that change between versions and
 * phone makers, so when blocking silently doesn't fire there's no way to tell
 * whether the service is dead, the events aren't arriving, or the address bar
 * just can't be read. This makes all three visible on the Blocks screen.
 */
object Watch {
    @Volatile var lastPackage: String = ""
    @Volatile var lastUrl: String = ""
    @Volatile var lastUrlSource: String = ""
    @Volatile var lastDecision: String = ""
    @Volatile var eventCount: Long = 0
    @Volatile var lastEventAt: Long = 0

    fun note(pkg: String) {
        lastPackage = pkg
        eventCount += 1
        lastEventAt = System.currentTimeMillis()
    }

    fun url(url: String, source: String) {
        lastUrl = url
        lastUrlSource = source
    }

    fun decision(text: String) {
        lastDecision = text
    }

    fun clearUrl() {
        lastUrl = ""
        lastUrlSource = ""
    }
}

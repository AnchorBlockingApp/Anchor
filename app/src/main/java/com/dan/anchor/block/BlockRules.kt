package com.dan.anchor.block

import android.content.Context
import com.dan.anchor.data.Prefs
import com.dan.anchor.data.Rule

sealed class Decision {
    object Allow : Decision()
    data class Block(val label: String, val reason: Reason) : Decision()
    enum class Reason { RULE, LIMIT_REACHED, ADULT, SELF_DEFENCE }
}

object BlockRules {

    /**
     * Browsers Anchor can read the address bar from. The second value is the
     * resource id of the URL field — nearly every Chromium-based browser reuses
     * Chrome's "url_bar", which is why one entry covers so many.
     */
    val BROWSERS: Map<String, String> = mapOf(
        "com.android.chrome" to "com.android.chrome:id/url_bar",
        "com.chrome.beta" to "com.chrome.beta:id/url_bar",
        "com.chrome.dev" to "com.chrome.dev:id/url_bar",
        "com.chrome.canary" to "com.chrome.canary:id/url_bar",
        "com.brave.browser" to "com.brave.browser:id/url_bar",
        "com.microsoft.emmx" to "com.microsoft.emmx:id/url_bar",
        "com.opera.browser" to "com.opera.browser:id/url_field",
        "com.opera.mini.native" to "com.opera.mini.native:id/url_field",
        "com.duckduckgo.mobile.android" to "com.duckduckgo.mobile.android:id/omnibarTextInput",
        "org.mozilla.firefox" to "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.focus" to "org.mozilla.focus:id/mozac_browser_toolbar_url_view",
        "com.sec.android.app.sbrowser" to "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.vivaldi.browser" to "com.vivaldi.browser:id/url_bar",
        "com.kiwibrowser.browser" to "com.kiwibrowser.browser:id/url_bar",
        "com.UCMobile.intl" to "com.UCMobile.intl:id/address_bar_text",
        "com.yandex.browser" to "com.yandex.browser:id/bro_omnibar_address_title_text"
    )

    /**
     * Every address-bar view id we know of, tried in turn when the browser's
     * own id doesn't match — Chromium forks and rebadged builds usually keep
     * one of these even when the package name is unfamiliar.
     */
    val URL_BAR_IDS: List<String> = listOf(
        "com.android.chrome:id/url_bar",
        "url_bar",
        "location_bar_edit_text",
        "url_field",
        "mozac_browser_toolbar_url_view",
        "omnibarTextInput",
        "address_bar_text",
        "editText_url",
        "search_box_text",
        "bro_omnibar_address_title_text"
    )

    /**
     * Seed list for the adult filter so it does something useful the moment you
     * switch it on. It is deliberately short — the real coverage comes from
     * importing a maintained public blocklist in Settings, which adds tens of
     * thousands of hosts. Keyword matching below catches most of the rest.
     */
    private val ADULT_SEED = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "spankbang.com", "chaturbate.com", "onlyfans.com",
        "stripchat.com", "bongacams.com", "livejasmin.com", "cam4.com",
        "tube8.com", "beeg.com", "motherless.com", "eporner.com", "txxx.com",
        "hqporner.com", "porntrex.com", "thumbzilla.com", "youjizz.com",
        "nudevista.com", "erome.com", "fapello.com", "rule34.xxx", "e-hentai.org",
        "nhentai.net", "hanime.tv", "hentaihaven.xxx", "adultfriendfinder.com"
    )

    /** Substrings that, in a hostname or path, almost always mean adult content. */
    private val ADULT_KEYWORDS = listOf(
        "porn", "xxx", "hentai", "camgirl", "camsex", "sexcam", "nsfw",
        "escort", "milf", "hookup", "fetish", "bdsm", "erotic", "nudes",
        "onlyfans", "stripcam", "adultvideo", "sexvideo", "18plus"
    )

    /** Search engines get a safe-search style guard on the query string itself. */
    private val SEARCH_HOSTS = listOf("google.", "bing.com", "duckduckgo.com", "yahoo.", "yandex.")

    fun host(url: String): String {
        var u = url.trim().lowercase()
        if (u.isEmpty()) return ""
        u = u.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        u = u.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(' ')
        return u.substringBefore(':')
    }

    private fun hostMatches(host: String, pattern: String): Boolean {
        val p = pattern.lowercase().removePrefix("www.")
        return host == p || host.endsWith(".$p")
    }

    /**
     * Decide what to do about a foreground app.
     */
    fun evaluateApp(context: Context, pkg: String): Decision {
        val prefs = Prefs.get(context)


        val rule = prefs.appRules().firstOrNull { it.target == pkg } ?: return Decision.Allow
        return verdictFor(prefs, rule)
    }

    /**
     * Decide what to do about a URL showing in a browser's address bar.
     */
    fun evaluateUrl(context: Context, rawUrl: String): Decision {
        val prefs = Prefs.get(context)
        val h = host(rawUrl)
        if (h.isEmpty() || !h.contains('.')) return Decision.Allow

        prefs.siteRules().firstOrNull { hostMatches(h, it.target) }?.let { rule ->
            return verdictFor(prefs, rule)
        }

        if (prefs.adultFilterOn) {
            val full = rawUrl.lowercase()
            val isSearch = SEARCH_HOSTS.any { h.contains(it) }

            if (ADULT_SEED.any { hostMatches(h, it) }) {
                return Decision.Block("", Decision.Reason.ADULT)
            }
            if (prefs.importedDomains.any { hostMatches(h, it) }) {
                return Decision.Block("", Decision.Reason.ADULT)
            }
            // On a search engine, check the query. Elsewhere, check the hostname
            // only — otherwise a legitimate article with "porn" in the URL trips it.
            val haystack = if (isSearch) full else h
            if (ADULT_KEYWORDS.any { haystack.contains(it) }) {
                return Decision.Block("", Decision.Reason.ADULT)
            }
        }

        return Decision.Allow
    }

    private fun verdictFor(prefs: Prefs, rule: Rule): Decision {
        if (rule.isHardBlock) return Decision.Block(rule.publicLabel, Decision.Reason.RULE)
        val used = prefs.usedMinutes(rule.target)
        return if (used >= rule.limitMinutes) {
            Decision.Block(rule.publicLabel, Decision.Reason.LIMIT_REACHED)
        } else {
            Decision.Allow
        }
    }

    fun isBrowser(pkg: String) = BROWSERS.containsKey(pkg)
}

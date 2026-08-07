package com.dan.anchor.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A rule is either an installed app (matched on package name) or a site
 * (matched on hostname). limitMinutes == 0 means "never allowed"; anything
 * higher gives you that many minutes per day before the block kicks in.
 */
data class Rule(
    val target: String,
    val label: String,
    val isApp: Boolean,
    val limitMinutes: Int = 0,
    /**
     * Kept out of the visible list. Meant for the case where someone else holds
     * your PIN — you shouldn't have to disclose what you're staying away from in
     * order to have them help you stay away from it.
     */
    val hidden: Boolean = false
) {
    val isHardBlock: Boolean get() = limitMinutes <= 0

    /** What the block screen is allowed to say out loud. */
    val publicLabel: String get() = if (hidden) "something you've blocked" else label
}

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("anchor", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(loadRules())
    val rules: StateFlow<List<Rule>> = _rules

    private val _strict = MutableStateFlow(sp.getBoolean(KEY_STRICT, false))
    val strictMode: StateFlow<Boolean> = _strict

    // ---------- rules ----------

    private fun loadRules(): List<Rule> {
        val raw = sp.getString(KEY_RULES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rule(
                    target = o.getString("target"),
                    label = o.optString("label", o.getString("target")),
                    isApp = o.getBoolean("isApp"),
                    limitMinutes = o.optInt("limitMinutes", 0),
                    hidden = o.optBoolean("hidden", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRules(list: List<Rule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("target", it.target)
                    .put("label", it.label)
                    .put("isApp", it.isApp)
                    .put("limitMinutes", it.limitMinutes)
                    .put("hidden", it.hidden)
            )
        }
        sp.edit().putString(KEY_RULES, arr.toString()).apply()
        _rules.value = list
    }

    fun upsertRule(rule: Rule) {
        val next = _rules.value.filterNot { it.target.equals(rule.target, true) && it.isApp == rule.isApp } + rule
        saveRules(next.sortedWith(compareBy({ !it.isApp }, { it.label.lowercase() })))
    }

    fun removeRule(rule: Rule) {
        saveRules(_rules.value.filterNot { it.target == rule.target && it.isApp == rule.isApp })
    }

    fun appRules(): List<Rule> = _rules.value.filter { it.isApp }
    fun siteRules(): List<Rule> = _rules.value.filter { !it.isApp }

    // ---------- adult filter ----------

    var adultFilterOn: Boolean
        get() = sp.getBoolean(KEY_ADULT, false)
        set(v) = sp.edit().putBoolean(KEY_ADULT, v).apply()

    /** Extra hostnames imported from a public blocklist, stored newline-separated. */
    var importedDomains: Set<String>
        get() = sp.getStringSet(KEY_IMPORTED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_IMPORTED, v).apply()

    // ---------- PIN ----------

    val hasPin: Boolean get() = sp.getString(KEY_PIN_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        sp.edit()
            .putString(KEY_PIN_SALT, saltHex)
            .putString(KEY_PIN_HASH, hash(pin, saltHex))
            .apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString(KEY_PIN_SALT, null) ?: return false
        val stored = sp.getString(KEY_PIN_HASH, null) ?: return false
        return hash(pin, salt) == stored
    }

    private fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        // Deliberately slow-ish: a 4-6 digit PIN has a small keyspace, so iterate.
        var bytes = (salt + pin).toByteArray()
        repeat(20_000) { bytes = md.digest(bytes) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- strict mode + cooldown ----------

    /**
     * Strict mode is the whole point of the app. While it's on you can't edit
     * rules or turn blocking off on impulse — you have to request a change,
     * wait out the cooldown, and still be sure about it when the timer expires.
     */
    fun enableStrict(cooldownMinutes: Int) {
        sp.edit()
            .putBoolean(KEY_STRICT, true)
            .putInt(KEY_COOLDOWN, cooldownMinutes)
            .remove(KEY_UNLOCK_AT)
            .apply()
        _strict.value = true
    }

    var cooldownMinutes: Int
        get() = sp.getInt(KEY_COOLDOWN, 60)
        set(v) = sp.edit().putInt(KEY_COOLDOWN, v).apply()

    /** Start the wait. Returns the epoch millis at which changes become possible. */
    fun requestUnlock(): Long {
        val existing = sp.getLong(KEY_UNLOCK_AT, 0L)
        if (existing > 0L) return existing
        val at = System.currentTimeMillis() + cooldownMinutes * 60_000L
        sp.edit().putLong(KEY_UNLOCK_AT, at).apply()
        return at
    }

    fun cancelUnlockRequest() = sp.edit().remove(KEY_UNLOCK_AT).apply()

    /** 0 = no request pending. Negative/zero remaining = unlocked and editable. */
    fun unlockAt(): Long = sp.getLong(KEY_UNLOCK_AT, 0L)

    fun isEditable(): Boolean {
        if (!_strict.value) return true
        val at = unlockAt()
        return at > 0L && System.currentTimeMillis() >= at
    }

    fun disableStrict() {
        sp.edit().putBoolean(KEY_STRICT, false).remove(KEY_UNLOCK_AT).apply()
        _strict.value = false
    }

    /**
     * A short window during which the strict-mode guard stands down.
     *
     * Anchor sends you into system Settings for two things: granting
     * accessibility, and granting uninstall protection. Without this the guard
     * bounces you straight back out of the very screen Anchor just opened.
     *
     * It can't be used as an escape hatch, because both buttons that set it only
     * appear when the thing they're granting is currently off — and when it's
     * off there's nothing for the guard to protect.
     */
    fun allowSettingsBriefly() {
        sp.edit().putLong(KEY_SETTINGS_GRACE, System.currentTimeMillis() + 120_000L).apply()
    }

    fun settingsAllowed(): Boolean =
        System.currentTimeMillis() < sp.getLong(KEY_SETTINGS_GRACE, 0L)

    // ---------- daily usage ----------

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun usageKey(target: String) = "usage_${today()}_$target"

    fun addUsageMillis(target: String, ms: Long) {
        val k = usageKey(target)
        sp.edit().putLong(k, sp.getLong(k, 0L) + ms).apply()
        pruneOldUsage()
    }

    fun usedMinutes(target: String): Int =
        (sp.getLong(usageKey(target), 0L) / 60_000L).toInt()

    fun usedMillis(target: String): Long = sp.getLong(usageKey(target), 0L)

    private fun pruneOldUsage() {
        val prefixToday = "usage_${today()}_"
        val stale = sp.all.keys.filter { it.startsWith("usage_") && !it.startsWith(prefixToday) }
        if (stale.isEmpty()) return
        sp.edit().apply { stale.forEach { remove(it) } }.apply()
    }

    // ---------- misc ----------

    var lastVerseIndex: Int
        get() = sp.getInt(KEY_LAST_VERSE, -1)
        set(v) = sp.edit().putInt(KEY_LAST_VERSE, v).apply()

    var pauseSeconds: Int
        get() = sp.getInt(KEY_PAUSE, 8)
        set(v) = sp.edit().putInt(KEY_PAUSE, v).apply()

    var blockCount: Int
        get() = sp.getInt(KEY_BLOCK_COUNT, 0)
        set(v) = sp.edit().putInt(KEY_BLOCK_COUNT, v).apply()

    /** Last chapter you were reading, as "bookId:chapter". */
    var lastRead: String
        get() = sp.getString(KEY_LAST_READ, "43:1") ?: "43:1"
        set(v) = sp.edit().putString(KEY_LAST_READ, v).apply()

    companion object {
        private const val KEY_RULES = "rules"
        private const val KEY_ADULT = "adult_filter"
        private const val KEY_IMPORTED = "imported_domains"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_STRICT = "strict"
        private const val KEY_COOLDOWN = "cooldown"
        private const val KEY_UNLOCK_AT = "unlock_at"
        private const val KEY_LAST_VERSE = "last_verse"
        private const val KEY_PAUSE = "pause_seconds"
        private const val KEY_BLOCK_COUNT = "block_count"
        private const val KEY_LAST_READ = "last_read"
        private const val KEY_SETTINGS_GRACE = "settings_grace"

        @Volatile private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(context).also { instance = it }
            }
    }
}

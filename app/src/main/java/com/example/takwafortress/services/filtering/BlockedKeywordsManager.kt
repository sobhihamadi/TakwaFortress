package com.example.takwafortress.services.filtering

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the list of blocked search keywords (up to 100).
 * Stored in SharedPreferences so they persist across reboots.
 *
 * Usage:
 *   val mgr = BlockedKeywordsManager(context)
 *   mgr.contains("porn")   // true
 *   mgr.addKeyword("xxx")
 *   mgr.getAll()           // full list
 */
class BlockedKeywordsManager(context: Context) {

    companion object {
        private const val PREFS_NAME  = "takwa_blocked_keywords"
        private const val KEY_WORDS   = "blocked_words"
        private const val MAX_WORDS   = 100

        // ── Default blocked keywords (Arabic + English) ──────────────────────
        // Add/remove from this list freely. All checks are lowercase.
        val DEFAULT_KEYWORDS = setOf(
            // English — explicit
            "porn", "pornhub", "xvideos", "xnxx", "xxx", "sex", "nude", "naked",
            "hentai", "onlyfans", "escort", "prostitute", "masturbat", "orgasm",
            "erotic", "fetish", "bdsm", "camgirl", "stripper", "blowjob",
            "hardcore", "nsfw", "lewd", "slutty", "boobs", "vagina", "penis",
            "anal sex", "threesome", "gangbang", "creampie", "milf", "teen sex",
            "amateur porn", "free porn", "watch porn", "download porn",
            "adult video", "adult movie", "adult film", "adult site",
            "sexy video", "hot video", "nude photo", "nude pic",

            // English — adjacent / gateway
            "dating app", "hookup", "tinder sex", "snapchat nude",
            "instagram nude", "reddit nsfw", "4chan", "rule34",
            "swimsuit model", "bikini model", "playboy", "penthouse",

            // Arabic — explicit (transliterated)
            "سكس", "نيك", "بزاز", "كس", "طيز", "شرموطة", "عاهرة",
            "افلام سكس", "صور سكس", "فيديو سكس", "مواقع سكس",
            "بنات عاريات", "نساء عاريات", "جنس", "ممارسة الجنس",
            "مص", "لحس", "شهوة", "إثارة جنسية", "إباحي",
            "موقع إباحي", "أفلام إباحية", "صور إباحية",

            // Urdu/Hindi transliterated
            "chudai", "chut", "lund", "gaand", "randi", "bhosdike",
            "harami", "sexy video", "desi porn", "indian sex",

            // French
            "porno", "sexe", "nue", "escorte",

            // Turkish
            "porno izle", "sex izle", "sikiş"
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Seed defaults on first run
        if (!prefs.contains(KEY_WORDS)) {
            saveAll(DEFAULT_KEYWORDS.toMutableSet())
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if [text] contains any blocked keyword.
     * Case-insensitive, trims whitespace.
     */
    fun contains(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return false
        return getAll().any { keyword -> lower.contains(keyword.lowercase()) }
    }

    /** Returns the matched keyword if found, null otherwise. */
    fun findMatch(text: String): String? {
        val lower = text.lowercase().trim()
        if (lower.isBlank()) return null
        return getAll().firstOrNull { keyword -> lower.contains(keyword.lowercase()) }
    }

    /** Returns all blocked keywords. */
    fun getAll(): Set<String> =
        prefs.getStringSet(KEY_WORDS, DEFAULT_KEYWORDS) ?: DEFAULT_KEYWORDS

    /**
     * Adds a new keyword. Enforces MAX_WORDS limit.
     * Returns false if limit reached.
     */
    fun addKeyword(word: String): Boolean {
        val current = getAll().toMutableSet()
        if (current.size >= MAX_WORDS) return false
        current.add(word.lowercase().trim())
        saveAll(current)
        return true
    }

    /** Removes a keyword. */
    fun removeKeyword(word: String) {
        val current = getAll().toMutableSet()
        current.remove(word.lowercase().trim())
        saveAll(current)
    }

    /** Replaces the entire list (up to MAX_WORDS). */
    fun setAll(words: Set<String>) {
        saveAll(words.take(MAX_WORDS).map { it.lowercase().trim() }.toMutableSet())
    }

    /** Resets to factory defaults. */
    fun resetToDefaults() {
        saveAll(DEFAULT_KEYWORDS.toMutableSet())
    }

    /** How many keywords are stored. */
    fun count(): Int = getAll().size

    // ── Private ───────────────────────────────────────────────────────────────

    private fun saveAll(words: MutableSet<String>) {
        prefs.edit().putStringSet(KEY_WORDS, words).apply()
    }
}
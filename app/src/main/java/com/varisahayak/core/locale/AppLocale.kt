package com.varisahayak.core.locale

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The languages VARI Sahayak ships.
 *
 * The route runs through Maharashtra, so Marathi is not an afterthought — a volunteer at a
 * water point is more likely to read Marathi than English, and the person they are helping
 * almost certainly does.
 */
enum class AppLocale(val tag: String, val endonym: String, val shortLabel: String) {
    /** Follow whatever the device is set to. */
    SYSTEM("", "", "SYS"),
    ENGLISH("en", "English", "EN"),
    MARATHI("mr", "मराठी", "MR"),
    HINDI("hi", "हिन्दी", "HI"),
    ;

    companion object {
        /** Only the explicit choices — SYSTEM is the default, not a menu entry. */
        val selectable: List<AppLocale> = listOf(ENGLISH, MARATHI, HINDI)

        fun fromTag(tag: String?): AppLocale =
            entries.firstOrNull { it.tag == tag && it != SYSTEM } ?: SYSTEM
    }
}

/**
 * Persists the chosen language and applies it to a [Context].
 *
 * ## Why SharedPreferences and not DataStore
 *
 * The value has to be readable *synchronously*, inside `Activity.attachBaseContext`, before
 * any coroutine scope exists. DataStore is asynchronous by design and cannot be read there
 * without blocking the main thread on a runBlocking, which is worse than one small
 * SharedPreferences file. Everything else in the app still uses DataStore.
 *
 * ## Why configuration wrapping and not LocaleManager
 *
 * Per-app languages via `LocaleManager` are API 33+. This app ships to minSdk 23, and
 * splitting the behaviour across two code paths would mean two things to test and one of
 * them rarely exercised. Wrapping the configuration works identically on every supported
 * release.
 */
object AppLocaleStore {

    private const val PREFS_NAME = "vari_locale"
    private const val KEY_TAG = "language_tag"

    fun current(context: Context): AppLocale {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppLocale.fromTag(prefs.getString(KEY_TAG, null))
    }

    fun save(context: Context, locale: AppLocale) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAG, locale.tag)
            .apply()
    }

    /**
     * Returns [base] re-configured for the stored language, or [base] unchanged when the
     * user has not chosen one.
     */
    fun wrap(base: Context): Context {
        val selected = current(base)
        if (selected == AppLocale.SYSTEM) return base

        val locale = Locale.forLanguageTag(selected.tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        // setLayoutDirection matters even though none of these three are RTL: without it a
        // device previously set to an RTL language keeps its direction after the switch.
        config.setLayoutDirection(locale)

        return base.createConfigurationContext(config)
    }
}

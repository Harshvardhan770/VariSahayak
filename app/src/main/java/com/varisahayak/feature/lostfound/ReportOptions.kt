package com.varisahayak.feature.lostfound

import androidx.annotation.StringRes
import com.varisahayak.R

/**
 * The closed vocabularies the report form offers instead of free text.
 *
 * Two reasons, and the second matters more than the speed.
 *
 * **Taps beat typing.** A volunteer filling this in is standing next to a frightened child,
 * often one-handed, often in the dark. Every field that can be a tap should be one.
 *
 * **The matching engine compares these by exact equality.** `LostFoundMatchingEngine`
 * matches gender and language with `a == b` on a lowercased string. Free text meant "M",
 * "male", "Male " and "boy" were four different genders, so the signal that should have
 * been the cheapest confirmation in the system almost never fired. A fixed set makes both
 * sides of the board write the same word.
 *
 * Every value here is stored in English regardless of the app's display language, for the
 * same reason: a report filed in Marathi and one filed in Hindi have to compare equal.
 * [labelRes] is what the volunteer reads; [wireValue] is what is stored and matched.
 */

/** Stored English value plus the localised label shown on the chip. */
interface ReportOption {
    val wireValue: String
    @get:StringRes val labelRes: Int
}

enum class GenderOption(
    override val wireValue: String,
    @StringRes override val labelRes: Int,
) : ReportOption {
    MALE("male", R.string.lostfound_gender_male),
    FEMALE("female", R.string.lostfound_gender_female),
    OTHER("other", R.string.lostfound_gender_other),
    ;

    companion object {
        fun fromWire(value: String?): GenderOption? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Age presets.
 *
 * The engine scores age numerically, with a ±2 tolerance for "compatible" and ±5 for
 * "roughly", so a band cannot be stored as a range without throwing that away. Each preset
 * writes a representative age instead, and the number stays editable underneath — a
 * volunteer who knows the child is seven types seven, and one who only knows "a small
 * child" taps once and still contributes a usable signal.
 *
 * Both sides of a pair tapping the same preset produce an exact match, which is the common
 * case and the one worth optimising.
 */
enum class AgePreset(
    val representativeAge: Int,
    @StringRes val labelRes: Int,
) {
    INFANT(1, R.string.lostfound_age_infant),
    YOUNG_CHILD(5, R.string.lostfound_age_young_child),
    CHILD(10, R.string.lostfound_age_child),
    TEENAGER(15, R.string.lostfound_age_teen),
    ADULT(35, R.string.lostfound_age_adult),
    ELDERLY(70, R.string.lostfound_age_elderly),
    ;

    companion object {
        /** The preset a typed age falls into, so the chips reflect a hand-entered number. */
        fun forAge(age: Int?): AgePreset? = when (age) {
            null -> null
            in 0..2 -> INFANT
            in 3..7 -> YOUNG_CHILD
            in 8..12 -> CHILD
            in 13..17 -> TEENAGER
            in 18..59 -> ADULT
            else -> ELDERLY
        }
    }
}

/**
 * Languages the person speaks.
 *
 * Ordered by what is actually heard on the Wari: Marathi first, then Hindi, then the
 * languages of the other states pilgrims travel from. "Not speaking" is a real and
 * important option — a frightened or non-verbal child is common, and recording it is more
 * useful than leaving the field empty, which the engine reads as "unknown".
 */
enum class LanguageOption(
    override val wireValue: String,
    @StringRes override val labelRes: Int,
) : ReportOption {
    MARATHI("marathi", R.string.lostfound_language_marathi),
    HINDI("hindi", R.string.lostfound_language_hindi),
    KANNADA("kannada", R.string.lostfound_language_kannada),
    TELUGU("telugu", R.string.lostfound_language_telugu),
    GUJARATI("gujarati", R.string.lostfound_language_gujarati),
    ENGLISH("english", R.string.lostfound_language_english),
    NOT_SPEAKING("not_speaking", R.string.lostfound_language_not_speaking),
    UNKNOWN("unknown", R.string.lostfound_language_unknown),
    ;

    companion object {
        fun fromWire(value: String?): LanguageOption? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * How the found person is right now.
 *
 * Found side only. This is triage information for whoever comes to help, so it is ordered
 * worst-first — the option that needs a responder is the one a volunteer should see without
 * scrolling.
 */
enum class ConditionOption(
    override val wireValue: String,
    @StringRes override val labelRes: Int,
) : ReportOption {
    NEEDS_MEDICAL("needs_medical", R.string.lostfound_condition_needs_medical),
    DISTRESSED("distressed", R.string.lostfound_condition_distressed),
    TIRED("tired", R.string.lostfound_condition_tired),
    SAFE("safe", R.string.lostfound_condition_safe),
    ;

    companion object {
        fun fromWire(value: String?): ConditionOption? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/**
 * Clothing colours, offered as toggles that build the description.
 *
 * Clothing is compared as a bag of words with Jaccard overlap, so consistent vocabulary on
 * both sides is worth more than richer prose on one. Two volunteers tapping "yellow" and
 * "blue" produce a real overlap; "mustard kurta" and "yellow top" produce none.
 *
 * The free-text box stays, for the detail that actually distinguishes one child in a yellow
 * shirt from another — but nobody has to type to contribute a matchable description.
 */
enum class ClothingColour(
    val wireValue: String,
    @StringRes val labelRes: Int,
) {
    RED("red", R.string.colour_red),
    ORANGE("orange", R.string.colour_orange),
    YELLOW("yellow", R.string.colour_yellow),
    GREEN("green", R.string.colour_green),
    BLUE("blue", R.string.colour_blue),
    PURPLE("purple", R.string.colour_purple),
    PINK("pink", R.string.colour_pink),
    WHITE("white", R.string.colour_white),
    BLACK("black", R.string.colour_black),
    BROWN("brown", R.string.colour_brown),
    GREY("grey", R.string.colour_grey),
    ;

    companion object {
        fun fromWire(value: String?): ClothingColour? =
            entries.firstOrNull { it.wireValue.equals(value?.trim(), ignoreCase = true) }
    }
}

package com.varisahayak.domain.model

/**
 * The initial incident categories from the product requirements.
 *
 * [wireName] is the value persisted to Postgres and accepted by the AI classifier. It is
 * decoupled from the enum name so a rename in Kotlin never silently breaks stored data.
 */
enum class IncidentCategory(val wireName: String) {
    MEDICAL("MEDICAL"),
    WATER("WATER"),
    LOST_PERSON("LOST_PERSON"),
    BLOCKED_ROAD("BLOCKED_ROAD"),
    SANITATION("SANITATION"),
    CROWD_SURGE("CROWD_SURGE"),
    OTHER("OTHER"),
    ;

    companion object {
        /** Unknown values degrade to [OTHER] rather than throwing: a server that learns a
         *  new category must never crash an older client in the field. */
        fun fromWire(value: String?): IncidentCategory =
            entries.firstOrNull { it.wireName == value } ?: OTHER
    }
}

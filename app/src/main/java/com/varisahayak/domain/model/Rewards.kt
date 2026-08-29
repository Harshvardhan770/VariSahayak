package com.varisahayak.domain.model

data class RewardProfile(
    val userId: String,
    val totalXp: Int,
    val level: Int,
    val badges: List<Badge>,
    val impact: ImpactSummary
) {
    val xpToNextLevel: Int get() = RewardEngine.xpForLevel(level + 1) - totalXp
    val progressToNextLevel: Float get() {
        val currentLevelXp = RewardEngine.xpForLevel(level)
        val nextLevelXp = RewardEngine.xpForLevel(level + 1)
        return (totalXp - currentLevelXp).toFloat() / (nextLevelXp - currentLevelXp).toFloat()
    }
}

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val iconResId: Int, // We'll map these to branded icons
    val unlockedAtEpochMillis: Long? = null
) {
    val isUnlocked: Boolean get() = unlockedAtEpochMillis != null
}

data class ImpactSummary(
    val incidentsResolved: Int,
    val sosResponses: Int,
    val peopleAssisted: Int,
    val lostFoundAssisted: Int
)

data class XPTransaction(
    val id: String,
    val userId: String,
    val amount: Int,
    val reason: String,
    val relatedEntityId: String?, // e.g. Incident Client ID
    val occurredAtEpochMillis: Long
)

object RewardEngine {
    /** Simple progression: Level 1 (0 XP), Level 2 (100 XP), Level 3 (300 XP), etc. */
    fun xpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (level - 1) * 100 + (level - 2) * (level - 1) * 50
    }

    fun calculateLevel(totalXp: Int): Int {
        var level = 1
        while (xpForLevel(level + 1) <= totalXp) {
            level++
        }
        return level
    }

    const val XP_RESOLVE_INCIDENT = 50
    const val XP_RESOLVE_SOS = 150
    const val XP_ASSIST_LOST_FOUND = 75
    const val XP_PROMPT_RESPONSE = 25
}

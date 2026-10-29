package com.xcluice.jetstrike

import android.content.SharedPreferences
import kotlin.math.max

/** One unlock requirement. [stat] is a key of [Progress]; an empty stat means the character is free. */
class Milestone(val stat: String, val need: Int, val text: String, val unit: String)

/** Index matches SKINS: what it takes to unlock each character. */
val MILESTONES: List<Milestone> = listOf(
    Milestone("", 0, "Starter character", ""),
    Milestone("kill", 20, "Get 20 kills", "kills"),
    Milestone("win", 1, "Win a match", "wins"),
    Milestone("kill", 75, "Get 75 kills", "kills"),
    Milestone("play", 10, "Play 10 matches", "matches"),
    Milestone("wave", 2, "Clear 2 story waves", "waves"),
    Milestone("win", 5, "Win 5 matches", "wins"),
    Milestone("boss", 1, "Defeat the Overlord in Story", "")
)

/** Lifetime stats that unlock characters, saved in SharedPreferences. */
class Progress(private val prefs: SharedPreferences) {
    private val keys = listOf("kill", "win", "play", "wave", "boss")
    private val v = HashMap<String, Int>()

    init { for (k in keys) v[k] = prefs.getInt("st_$k", 0) }

    fun get(stat: String): Int = v[stat] ?: 0

    fun unlocked(i: Int): Boolean {
        val m = MILESTONES.getOrNull(i) ?: return false
        return m.stat.isEmpty() || get(m.stat) >= m.need
    }

    fun count(): Int = SKINS.indices.count { unlocked(it) }

    /** Adds [n] to a stat (story waves keep the best value instead). Returns the characters this just unlocked. */
    fun bump(stat: String, n: Int): List<Int> {
        if (!v.containsKey(stat)) return emptyList()
        val before = SKINS.indices.filter { unlocked(it) }.toSet()
        v[stat] = if (stat == "wave") max(get(stat), n) else get(stat) + n
        prefs.edit().putInt("st_$stat", get(stat)).apply()
        return SKINS.indices.filter { unlocked(it) && it !in before }
    }

    fun progressText(i: Int): String {
        val m = MILESTONES[i]
        return if (m.stat.isEmpty()) "" else "${minOf(get(m.stat), m.need)} / ${m.need}"
    }

    fun fraction(i: Int): Float {
        val m = MILESTONES[i]
        return if (m.stat.isEmpty()) 1f else (get(m.stat).toFloat() / m.need).coerceIn(0f, 1f)
    }
}

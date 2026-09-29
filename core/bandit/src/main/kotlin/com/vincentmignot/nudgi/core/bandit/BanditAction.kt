package com.vincentmignot.nudgi.core.bandit

/**
 * What the bandit can do when a rule fires. Declaration order follows the friction ladder, and
 * [id] is what `events` records.
 */
enum class BanditAction(
    val id: String,
) {
    Nothing("nothing"),
    Notification("notification"),
    Overlay("overlay"),
    CountdownOverlay("countdown_overlay"),
    ForcedClose("forced_close"),
    ;

    companion object {
        fun fromId(id: String?): BanditAction? = entries.firstOrNull { it.id == id }
    }
}

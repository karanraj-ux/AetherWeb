package com.aetherweb.app

import kotlin.random.Random

/**
 * Human-like "thinking" buffer for game bots.
 *
 * Every bot move goes through this so bots feel human instead of instant.
 * - Normal: 800–1500ms randomized thinking pause.
 * - Fast mode: no delay (for future fast-match modes).
 */
object BotThinking {
    fun delayMs(fastMode: Boolean = false): Long =
        if (fastMode) 0L else 800L + Random.nextLong(700L)

    /** Display label while a bot is thinking. */
    const val THINKING_LABEL = "🤖 thinking…"
}

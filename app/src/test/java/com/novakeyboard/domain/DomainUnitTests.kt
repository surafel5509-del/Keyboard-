package com.novakeyboard.domain

import org.junit.Assert.fail
import org.junit.Test

/**
 * JUnit 4 entry point for the plain-JVM domain suite in `DomainTestSuite.kt`.
 *
 * Each scenario runs as its own JUnit test so `./gradlew :app:testDebugUnitTest`
 * reports a per-area result (shift, editing, suggestions, autocorrect,
 * preferences, languages, clipboard, privacy, emoji). The same scenarios can be
 * executed without Gradle through `DomainTestSuiteKt.main`, which is handy when
 * only a JDK and the Kotlin compiler are available.
 */
class DomainUnitTests {

    private fun scenario(name: String, body: () -> Unit) {
        resetStats()
        body()
        if (failures > 0) {
            fail("$name: $failures of $checks checks failed (see stdout for details)")
        }
    }

    @Test
    fun shiftAndCapsLock() = scenario("shift and caps lock", ::shiftAndCaps)

    @Test
    fun insertionAndBackspace() = scenario("insertion and backspace", ::insertionAndBackspace)

    @Test
    fun suggestionsAndRanking() = scenario("suggestions and ranking", ::suggestionsAndRanking)

    @Test
    fun autoCorrect() = scenario("autocorrect", ::autoCorrect)

    @Test
    fun preferences() = scenario("preferences", ::preferences)

    @Test
    fun languageSwitchingAndGeez() = scenario("language switching and Geez input", ::languagesAndGeez)

    @Test
    fun clipboardLimits() = scenario("clipboard limits and capture policy", ::clipboardLimits)

    @Test
    fun sensitiveFields() = scenario("sensitive fields", ::sensitiveFields)

    @Test
    fun emojiIndex() = scenario("emoji index", ::emojiIndex)
}

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

    // Lambdas (not method references) so the scenarios stay bound to the
    // top-level functions in DomainTestSuite.kt instead of recursing into the
    // test methods below.
    @Test
    fun testShiftAndCapsLock() = scenario("shift and caps lock") { shiftAndCaps() }

    @Test
    fun testInsertionAndBackspace() = scenario("insertion and backspace") { insertionAndBackspace() }

    @Test
    fun testSuggestionsAndRanking() = scenario("suggestions and ranking") { suggestionsAndRanking() }

    @Test
    fun testAutoCorrect() = scenario("autocorrect") { autoCorrect() }

    @Test
    fun testPreferences() = scenario("preferences") { preferences() }

    @Test
    fun testLanguageSwitchingAndGeez() = scenario("language switching and Geez input") { languagesAndGeez() }

    @Test
    fun testClipboardLimits() = scenario("clipboard limits and capture policy") { clipboardLimits() }

    @Test
    fun testSensitiveFields() = scenario("sensitive fields") { sensitiveFields() }

    @Test
    fun testEmojiIndex() = scenario("emoji index") { emojiIndex() }
}

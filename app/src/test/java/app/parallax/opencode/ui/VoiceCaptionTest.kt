package app.parallax.opencode.ui

import app.parallax.opencode.ui.screens.voice.voiceChunkEnd
import app.parallax.opencode.ui.voice.captionPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCaptionTest {
    @Test fun streamsProseWhileHoldingCodeAndLinks() {
        val prefix = "The build passed. "
        for (span in listOf("```kotlin\n", "`", "[label](")) {
            val text = prefix + span + "long content ".repeat(10)
            assertEquals(prefix.trimEnd().length, voiceChunkEnd(text, 0))
        }
        val prose = "another word ".repeat(12)
        val end = voiceChunkEnd(prose, 0)
        assertTrue(end in 64 until prose.length && prose[end - 1].isWhitespace())
    }

    @Test fun followupPhrasesKeepSentenceRhythm() {
        val sentence = "These words belong together in one sentence and should still sound like one sentence."
        val unfinished = sentence.dropLast(1)
        assertEquals(0, voiceChunkEnd(unfinished, 0, firstPhrase = false))
        assertEquals(sentence.length, voiceChunkEnd(sentence, 0, firstPhrase = false))
    }

    @Test fun captionsStayOnPageUntilPlaybackReachesNextWord() {
        val first = "abcdef ".repeat(11)
        val text = first + "next page."
        assertEquals(first.trim(), captionPage(text, 0))
        assertEquals(first.trim(), captionPage(text, first.length - 2))
        assertEquals("next page.", captionPage(text, first.length))
    }
}

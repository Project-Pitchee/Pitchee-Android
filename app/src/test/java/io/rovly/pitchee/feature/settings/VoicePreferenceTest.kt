package io.rovly.pitchee.feature.settings

import io.rovly.pitchee.feature.settings.VoicePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePreferenceTest {
    @Test
    fun parsesStableIosCompatibleValues() {
        assertEquals(VoicePreference.MASCULINE, VoicePreference.fromRaw("masculine"))
        assertEquals(VoicePreference.FEMININE, VoicePreference.fromRaw("feminine"))
        assertNull(VoicePreference.fromRaw("undecided"))
        assertNull(VoicePreference.fromRaw("female"))
    }
}

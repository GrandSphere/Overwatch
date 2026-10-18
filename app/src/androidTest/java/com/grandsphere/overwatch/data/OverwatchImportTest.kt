package com.grandsphere.overwatch.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grandsphere.overwatch.domain.model.OverwatchConfig
import com.grandsphere.overwatch.domain.model.RepeatKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverwatchImportTest {
    @Test
    fun roundTripExportPreservesNamesAndIntervals() {
        val configs = listOf(
            OverwatchConfig(
                name = "Round Trip A",
                intervalMs = 120_000L,
                repeatKind = RepeatKind.SINGLE,
            ),
            OverwatchConfig(
                name = "Round Trip B",
                intervalMs = 900_000L,
                repeatKind = RepeatKind.COUNT,
                repeatCount = 2,
            ),
        )
        val json = OverwatchExchange.overwatchesJson(configs)
        assertEquals(OverwatchExchange.FORMAT_OVERWATCHES, OverwatchExchange.detectFormat(json))
        val parsed = OverwatchExchange.parseOverwatches(json)
        assertEquals(2, parsed.size)
        assertEquals("Round Trip A", parsed[0].name)
        assertEquals(120_000L, parsed[0].intervalMs)
        assertEquals(RepeatKind.SINGLE, parsed[0].repeatKind)
        assertEquals("Round Trip B", parsed[1].name)
        assertEquals(2, parsed[1].repeatCount)
    }

    @Test
    fun fixtureTwoConfigsParses() {
        val json = readResource("import/overwatch-import-two.json")
        val parsed = OverwatchExchange.parseOverwatches(json)
        assertEquals(2, parsed.size)
        assertEquals("Import Test Alpha", parsed[0].name)
        assertEquals("Import Test Beta", parsed[1].name)
        assertEquals(RepeatKind.COUNT, parsed[1].repeatKind)
        assertEquals(3, parsed[1].repeatCount)
    }

    @Test
    fun fixtureMinimalParses() {
        val json = readResource("import/overwatch-import-minimal.json")
        val parsed = OverwatchExchange.parseOverwatches(json)
        assertEquals(1, parsed.size)
        assertEquals("Import Test Solo", parsed[0].name)
    }

    @Test
    fun rejectsWrongFormat() {
        val json = readResource("import/overwatch-import-minimal.json")
            .replace(OverwatchExchange.FORMAT_OVERWATCHES, "not-overwatch")
        assertThrows(Exception::class.java) {
            OverwatchExchange.parseOverwatches(json)
        }
    }

    private fun readResource(path: String): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open(path).bufferedReader().readText()
    }
}

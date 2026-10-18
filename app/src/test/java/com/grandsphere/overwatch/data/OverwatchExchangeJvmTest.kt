package com.grandsphere.overwatch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverwatchExchangeJvmTest {
    @Test
    fun uniqueTempNameSkipsTaken() {
        val taken = setOf("temp1", "temp2", "temp3")
        val name = OverwatchExchange.uniqueTempName(taken)
        assertTrue(name.startsWith("temp"))
        assertTrue(name !in taken)
        assertEquals("temp4", name)
    }
}

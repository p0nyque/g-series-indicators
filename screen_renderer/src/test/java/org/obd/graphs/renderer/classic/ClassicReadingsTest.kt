/*
 * Copyright 2019-2026, Tomasz Żebrowski
 *
 * <p>Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.obd.graphs.renderer.classic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicReadingsTest {
    private val oil = CLASSIC_MAIN[0]
    private val boost = CLASSIC_MAIN[2]
    private val intake = CLASSIC_SUB[0]
    private val oilLevel = CLASSIC_SUB[2]
    private val starterGenerator = CLASSIC_SUB[4]
    private val voltage = CLASSIC_SUB[5]

    @Test
    fun `readout uses the slot's decimals`() {
        assertEquals("98", ClassicReadings.reading(oil, 98.4, stale = false, baroKpa = null).text)
        assertEquals("14.1", ClassicReadings.reading(voltage, 14.06, stale = false, baroKpa = null).text)
    }

    @Test
    fun `no reading or a stale one rests with dashes`() {
        assertEquals(ClassicReading(null, NO_VALUE), ClassicReadings.reading(oil, null, stale = false, baroKpa = null))
        assertEquals(ClassicReading(null, NO_VALUE), ClassicReadings.reading(oil, 98.0, stale = true, baroKpa = null))
        assertEquals(ClassicReading(null, NO_VALUE), ClassicReadings.reading(oil, Double.NaN, stale = false, baroKpa = null))
    }

    @Test
    fun `a value beyond the scale pins the pointer but shows the real value`() {
        val reading = ClassicReadings.reading(oil, 163.0, stale = false, baroKpa = null)
        assertEquals(1f, reading.fraction!!, 0f)
        assertEquals("163", reading.text)
        assertEquals(0f, ClassicReadings.reading(oil, 20.0, stale = false, baroKpa = null).fraction!!, 0f)
    }

    @Test
    fun `negative values use the typographic minus`() {
        assertEquals("−5", ClassicReadings.reading(intake, -5.0, stale = false, baroKpa = null).text)
    }

    @Test
    fun `boost is relative to baro`() {
        val reading = ClassicReadings.reading(boost, 2.43, stale = false, baroKpa = 101.0)
        assertEquals("1.42", reading.text)
        assertEquals(1.42f / 2f, reading.fraction!!, 0.001f)
    }

    @Test
    fun `boost falls back to the standard atmosphere until baro arrives`() {
        assertEquals("0.00", ClassicReadings.reading(boost, 1.013, stale = false, baroKpa = null).text)
    }

    @Test
    fun `boost target goes through the same conversion as the actual value`() {
        val actual = ClassicReadings.value(boost, 2.6, 95.0)!!
        val target = ClassicReadings.value(boost, 2.6, 95.0)!!
        assertEquals(actual, target, 0.0)
        assertEquals(1.65, target, 0.0001)
        assertEquals("1.65", ClassicReadings.formatTarget(target))
    }

    @Test
    fun `starter-generator readout always carries its sign`() {
        assertEquals("+3.2", ClassicReadings.reading(starterGenerator, 3.2, stale = false, baroKpa = null).text)
        assertEquals("−1.0", ClassicReadings.reading(starterGenerator, -1.0, stale = false, baroKpa = null).text)
        assertEquals("0.0", ClassicReadings.reading(starterGenerator, -0.04, stale = false, baroKpa = null).text)
    }

    @Test
    fun `centre-zero dial rests at zero`() {
        assertEquals(0.5f, ClassicReadings.restFraction(starterGenerator), 0f)
        assertEquals(0f, ClassicReadings.restFraction(oil), 0f)
    }

    @Test
    fun `oil level has no readout and rests when not measured`() {
        val reading = ClassicReadings.reading(oilLevel, 72.0, stale = false, baroKpa = null)
        assertEquals("", reading.text)
        assertEquals(0.72f, reading.fraction!!, 0.001f)
        assertNull(ClassicReadings.reading(oilLevel, 255.0, stale = false, baroKpa = null).fraction)
        assertEquals(ClassicReading(null, ""), ClassicReadings.reading(oilLevel, null, stale = false, baroKpa = null))
    }

    @Test
    fun `regen lamp and distance`() {
        assertEquals(ClassicRegen(lit = false, distance = "312"), ClassicReadings.regen(0.0, 312.4))
        assertEquals(ClassicRegen(lit = true, distance = "312"), ClassicReadings.regen(1.0, 312.0))
        assertEquals(ClassicRegen(lit = false, distance = NO_DISTANCE), ClassicReadings.regen(null, null))
        assertFalse(ClassicReadings.regen(Double.NaN, null).lit)
        assertTrue(ClassicReadings.regen(1.0, null).lit)
    }
}

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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.obd.graphs.bl.query.ClassicPids

class ClassicDialsTest {
    private val slots = CLASSIC_MAIN + CLASSIC_SUB

    @Test
    fun `four main dials and six sub-dials in spec order`() {
        assertEquals(
            listOf(ClassicPids.OIL_TEMP, ClassicPids.COOLANT_TEMP, ClassicPids.BOOST_ACTUAL, ClassicPids.GEARBOX_OIL_TEMP),
            CLASSIC_MAIN.map { it.pidId }
        )
        assertEquals(
            listOf(
                ClassicPids.INTAKE_AIR_TEMP,
                ClassicPids.EXHAUST_TEMP,
                ClassicPids.OIL_LEVEL,
                ClassicPids.BATTERY_48V_SOC,
                ClassicPids.STARTER_GENERATOR_POWER,
                ClassicPids.MODULE_VOLTAGE
            ),
            CLASSIC_SUB.map { it.pidId }
        )
        CLASSIC_MAIN.forEach { assertEquals(ClassicKind.MAIN, it.kind) }
        CLASSIC_SUB.forEach { assertTrue(it.kind != ClassicKind.MAIN) }
    }

    @Test
    fun `every numeral lies on its scale`() {
        slots.forEach { slot ->
            slot.scale.numerals.forEach {
                assertTrue("${slot.pidId} ${it.text}", it.value >= slot.scale.min && it.value <= slot.scale.max)
            }
            assertEquals(slot.scale.min, slot.scale.numerals.first().value, 0.0)
            assertEquals(slot.scale.max, slot.scale.numerals.last().value, 0.0)
        }
    }

    @Test
    fun `printed zones lie inside the range`() {
        slots.forEach { slot ->
            slot.scale.zones.forEach {
                assertTrue("${slot.pidId} $it", it.from >= slot.scale.min && it.to <= slot.scale.max && it.from < it.to)
            }
        }
    }

    @Test
    fun `main dial numerals sit on major ticks`() {
        CLASSIC_MAIN.forEach { slot ->
            val scale = slot.scale
            val majors = (0..scale.tickSteps step scale.minorPerMajor).map { scale.min + (scale.max - scale.min) * it / scale.tickSteps }
            scale.numerals.forEach { numeral -> assertTrue("${slot.pidId} ${numeral.text}", majors.any { kotlin.math.abs(it - numeral.value) < 1e-9 }) }
        }
    }

    @Test
    fun `every drawn PID is queried`() {
        assertTrue(ClassicPids.ALL.containsAll(CLASSIC_DRAWN_PIDS))
        assertEquals(ClassicPids.ALL, CLASSIC_DRAWN_PIDS)
    }
}

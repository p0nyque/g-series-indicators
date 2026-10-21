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
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND = 1_000_000_000L
private const val FRAME = SECOND / 60

class ClassicFrameStatesTest {
    @Test
    fun `needle starts on the value, then eases to the next one`() {
        val states = ClassicFrameStates()

        assertEquals(0.2f, states.needle(1, 0.2f, nowNanos = SECOND))
        val next = states.needle(1, 0.8f, nowNanos = SECOND + FRAME)

        assertTrue("$next", next > 0.2f && next < 0.8f)
    }

    @Test
    fun `needle of each PID eases on its own`() {
        val states = ClassicFrameStates()
        states.needle(1, 0f, SECOND)
        states.needle(2, 1f, SECOND)

        assertEquals(1f, states.needle(2, 1f, SECOND + FRAME))
        assertTrue(states.needle(1, 1f, SECOND + FRAME) < 1f)
    }

    @Test
    fun `dial goes stale when no new reading arrives`() {
        val states = ClassicFrameStates(staleAfterNanos = 5 * SECOND)
        val reading = Any()

        assertFalse(states.isStale(1, reading, hasValue = true, nowNanos = SECOND))
        assertFalse(states.isStale(1, reading, hasValue = true, nowNanos = 5 * SECOND))
        assertTrue(states.isStale(1, reading, hasValue = true, nowNanos = 7 * SECOND))
        // A new reading (the collector replaces the source object) makes it live again.
        assertFalse(states.isStale(1, Any(), hasValue = true, nowNanos = 8 * SECOND))
    }

    @Test
    fun `dial without a value is never stale`() {
        val states = ClassicFrameStates(staleAfterNanos = SECOND)

        states.isStale(1, null, hasValue = false, nowNanos = SECOND)
        assertFalse(states.isStale(1, null, hasValue = false, nowNanos = 100 * SECOND))
    }

    @Test
    fun `needle eases towards the value independent of the frame rate`() {
        val oneStep = easeNeedle(0f, 1f, elapsedSeconds = 0.1f)
        var twoSteps = easeNeedle(0f, 1f, elapsedSeconds = 0.05f)
        twoSteps = easeNeedle(twoSteps, 1f, elapsedSeconds = 0.05f)

        assertEquals(oneStep, twoSteps, 0.001f)
        assertTrue("$oneStep", oneStep > 0f && oneStep < 1f)
        assertEquals(0.632f, easeNeedle(0f, 1f, NEEDLE_TIME_CONSTANT_SECONDS), 0.001f)
    }

    @Test
    fun `needle snaps on the first frame, after a gap and when close`() {
        assertEquals(0.7f, easeNeedle(Float.NaN, 0.7f, 0.016f))
        assertEquals(0.7f, easeNeedle(0.1f, 0.7f, NEEDLE_MAX_GAP_SECONDS + 0.1f))
        assertEquals(0.7f, easeNeedle(0.6999f, 0.7f, 0.5f))
        assertEquals(0.5f, easeNeedle(0.5f, 0.7f, 0f))
    }
}

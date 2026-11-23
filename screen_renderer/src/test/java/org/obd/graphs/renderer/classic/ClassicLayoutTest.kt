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

class ClassicLayoutTest {
    private val dhu = ClassicLayout.of(ClassicBox(0f, 0f, 800f, 480f))
    private val wide = ClassicLayout.of(ClassicBox(0f, 0f, 1920f, 720f))

    // Android Auto on the car's 8:3 widescreen (DHU): the home-view side panel, and the app next to Spotify.
    private val panel12 = ClassicLayout.of(ClassicBox(1216f, 10f, 1804f, 658f))
    private val panel10 = ClassicLayout.of(ClassicBox(792f, 10f, 1178f, 422f))
    private val beside12 = ClassicLayout.of(ClassicBox(116f, 10f, 1284f, 606f))
    private val beside10 = ClassicLayout.of(ClassicBox(102f, 10f, 848f, 370f))
    private val split = ClassicLayout.of(ClassicBox(22f, 10f, 464f, 352f))
    private val all = listOf(dhu, wide, panel12, panel10, beside12, beside10, split)

    private val boost = CLASSIC_MAIN[2]
    private val starterGenerator = CLASSIC_SUB[4]
    private val intake = CLASSIC_SUB[0]

    @Test
    fun `bands split the height 55, 32 and 13 percent`() {
        listOf(dhu, wide).forEach {
            val h = it.area.height
            assertEquals(h * 0.55f, it.mainBand.height, 0.5f)
            assertEquals(h * 0.32f, it.subBand.height, 0.5f)
            assertEquals(h * 0.13f, it.stripBand.height, 0.5f)
            assertEquals(it.area.bottom, it.stripBand.bottom, 0.01f)
        }
    }

    @Test
    fun `tall side panels stack the dials, every wider area keeps one row each`() {
        listOf(panel12, panel10).forEach { assertTrue("${it.area}", it.stacked) }
        listOf(dhu, wide, beside12, beside10, split).forEach { assertFalse("${it.area}", it.stacked) }
    }

    @Test
    fun `stacking makes the main dials larger than one row would`() {
        // One row of four in the 588 x 648 panel gives r ~ 64.
        assertTrue("r = ${panel12.main[0].radius}", panel12.main[0].radius > 75f)
        assertTrue("r = ${panel10.main[0].radius}", panel10.main[0].radius > 45f)
    }

    @Test
    fun `stacked order reads left to right, top to bottom, engine above electrical`() {
        val (oil, coolant, boost, gearbox) = panel12.main
        assertEquals(oil.cy, coolant.cy, 0.01f)
        assertEquals(boost.cy, gearbox.cy, 0.01f)
        assertTrue(oil.cx < coolant.cx && boost.cx < gearbox.cx && oil.cy < boost.cy)
        assertEquals(oil.cx, boost.cx, 0.01f)
        val engine = panel12.sub.take(3)
        val electrical = panel12.sub.drop(3)
        engine.forEach { assertEquals(engine[0].cy, it.cy, 0.01f) }
        electrical.forEach { assertTrue(it.cy > engine[0].cy) }
    }

    @Test
    fun `stacked bands split the height 55, 35 and 10 percent`() {
        val h = panel12.area.height
        assertEquals(h * 0.55f, panel12.mainBand.height, 0.5f)
        assertEquals(h * 0.35f, panel12.subBand.height, 0.5f)
        assertEquals(h * 0.10f, panel12.stripBand.height, 0.5f)
    }

    @Test
    fun `every dial stays inside its own cell and the area`() {
        all.forEach { layout ->
            layout.main.forEach {
                assertTrue("main $it", it.cell.contains(it.bounds(ClassicKind.MAIN)))
                assertTrue(layout.area.contains(it.bounds(ClassicKind.MAIN)))
            }
            layout.sub.forEach {
                assertTrue("sub $it", it.cell.contains(it.bounds(ClassicKind.SUB)))
                assertTrue(layout.area.contains(it.bounds(ClassicKind.SUB)))
            }
            assertTrue(layout.stripBand.contains(layout.lamp))
        }
    }

    @Test
    fun `no two dials overlap`() {
        all.forEach { layout ->
            val boxes = layout.main.map { it.bounds(ClassicKind.MAIN) } + layout.sub.map { it.bounds(ClassicKind.SUB) }
            boxes.forEachIndexed { i, a ->
                boxes.drop(i + 1).forEach { b -> assertFalse("$a overlaps $b", a.overlaps(b)) }
            }
        }
    }

    @Test
    fun `dhu sizes match the spec`() {
        // ~180 px main dials, ~130 px sub-dials at 800 x 480.
        assertEquals(87.4f, dhu.main[0].radius, 0.5f)
        assertEquals(64f, dhu.sub[0].radius, 0.5f)
        assertEquals(1f, dhu.sub[0].k, 0.01f)
    }

    @Test
    fun `on a wide screen the height sets the size and the extra width becomes even gaps`() {
        val radius = wide.main[0].radius
        assertEquals(wide.mainBand.height * 0.94f * (0.5f - 0.035f), radius, 0.5f)
        val gaps = wide.main.zipWithNext { a, b -> b.cx - a.cx }
        gaps.forEach { assertEquals(gaps[0], it, 0.01f) }
        assertTrue(gaps[0] > 2 * radius)
    }

    @Test
    fun `area offset moves everything`() {
        val shifted = ClassicLayout.of(ClassicBox(100f, 50f, 900f, 530f))
        assertEquals(dhu.main[0].cx + 100f, shifted.main[0].cx, 0.01f)
        assertEquals(dhu.sub[3].cy + 50f, shifted.sub[3].cy, 0.01f)
    }

    @Test
    fun `main dial sweeps 240 degrees from 7 to 5 o'clock`() {
        assertEquals(-120f, ClassicLayout.angle(boost, 0f), 0.01f)
        assertEquals(120f, ClassicLayout.angle(boost, 1f), 0.01f)
        assertEquals(0f, ClassicLayout.angle(boost, 0.5f), 0.01f)
    }

    @Test
    fun `angles clamp beyond both ends`() {
        assertEquals(-120f, ClassicLayout.angle(boost, -0.3f), 0.01f)
        assertEquals(120f, ClassicLayout.angle(boost, 1.4f), 0.01f)
        assertEquals(45f, ClassicLayout.angle(intake, 2f), 0.01f)
    }

    @Test
    fun `rest is the scale start, and zero at the top for the centre-zero dial`() {
        assertEquals(-120f, ClassicLayout.angle(boost, null), 0.01f)
        assertEquals(-45f, ClassicLayout.angle(intake, null), 0.01f)
        assertEquals(0f, ClassicLayout.angle(starterGenerator, null), 0.01f)
        assertEquals(-45f, ClassicLayout.angle(starterGenerator, 0f), 0.01f)
    }

    @Test
    fun `points are measured clockwise from 12 o'clock`() {
        assertEquals(100f, ClassicLayout.x(100f, 10f, 0f), 0.001f)
        assertEquals(90f, ClassicLayout.y(100f, 10f, 0f), 0.001f)
        assertEquals(110f, ClassicLayout.x(100f, 10f, 90f), 0.001f)
    }

    @Test
    fun `no text below 11 px`() {
        assertEquals(11f, ClassicLayout.textSize(9.5f), 0f)
        assertEquals(34f, ClassicLayout.textSize(34f), 0f)
    }
}

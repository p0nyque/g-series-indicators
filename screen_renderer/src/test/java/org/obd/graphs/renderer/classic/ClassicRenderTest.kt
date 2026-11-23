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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.Pid
import org.obd.graphs.bl.query.ClassicPids
import org.obd.metrics.api.model.ObdMetric
import org.obd.metrics.command.obd.ObdCommand
import org.obd.metrics.pid.PidDefinition
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// The mockups' values (scripts/mockups/classic_dashboard.py).
private val MOCKUP_VALUES: Map<Long, Number> =
    mapOf(
        ClassicPids.OIL_TEMP to 98,
        ClassicPids.COOLANT_TEMP to 91,
        ClassicPids.BOOST_ACTUAL to 2.433,
        ClassicPids.BOOST_TARGET to 2.613,
        ClassicPids.BARO_PRESSURE to 101.3,
        ClassicPids.GEARBOX_OIL_TEMP to 78,
        ClassicPids.INTAKE_AIR_TEMP to 24,
        ClassicPids.EXHAUST_TEMP to 310,
        ClassicPids.OIL_LEVEL to 72,
        ClassicPids.BATTERY_48V_SOC to 72,
        ClassicPids.STARTER_GENERATOR_POWER to 3.2,
        ClassicPids.MODULE_VOLTAGE to 14.1,
        ClassicPids.DPF_REGEN_ACTIVE to 0,
        ClassicPids.DPF_REGEN_DISTANCE to 312
    )

/**
 * Draws the whole screen on a real (native) canvas: no DHU is needed to see it. The PNGs land in
 * screen_renderer/build/classic-render/ for a look; the assertions catch a blank or crashing frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClassicRenderTest {
    private val outDir = File("build/classic-render").apply { mkdirs() }

    @Test
    fun `renders the mockup values at 800 x 480`() {
        val bitmap = render(800, 480, running = true)
        save(bitmap, "classic-800x480.png")
        assertEquals(Color.BLACK, bitmap.getPixel(2, 2))
        assertTrue("no orange pointer or glow drawn", orangePixels(bitmap) > 200)
    }

    @Test
    fun `renders the mockup values at 1920 x 720`() {
        val bitmap = render(1920, 720, running = true)
        save(bitmap, "classic-1920x720.png")
        assertTrue(orangePixels(bitmap) > 400)
    }

    @Test
    fun `renders the regeneration state`() {
        val bitmap = render(800, 480, running = true, values = MOCKUP_VALUES + (ClassicPids.DPF_REGEN_ACTIVE to 1) + (ClassicPids.EXHAUST_TEMP to 610))
        save(bitmap, "classic-800x480-regen.png")
    }

    @Test
    fun `not connected rests every pointer`() {
        val connected = orangePixels(render(800, 480, running = true))
        val bitmap = render(800, 480, running = false)
        save(bitmap, "classic-800x480-disconnected.png")
        assertTrue("glow should be gone when disconnected", orangePixels(bitmap) < connected / 2)
    }

    @Test
    fun `printed zones use the BMW M stripe colours`() {
        val bitmap = render(800, 480, running = false)
        assertTrue("no M red zone drawn", pixels(bitmap, Color.parseColor("#F11A22")) > 50)
        assertTrue("no M blue zone drawn", pixels(bitmap, Color.parseColor("#008AC9")) > 10)
    }

    // Android Auto's visible area on the DHU (800 x 480): full screen and split screen next to media.
    private val aaFull = ClassicBox(12f, 90f, 788f, 390f)
    private val aaSplit = ClassicBox(22f, 10f, 464f, 352f)

    // The car (G31 LCI): the DHU set up as an 8:3 widescreen, 12.3" (1080p video cropped to 1920 x 720) and the
    // smaller panel (720p cropped to 1280 x 480), dpi 160. Areas AA gave the app, as logged by SurfaceController.
    private val carAreas =
        mapOf(
            "car-1920x720-full" to ClassicBox(116f, 10f, 1908f, 606f),
            "car-1920x720-home-panel" to ClassicBox(1216f, 10f, 1804f, 658f),
            "car-1920x720-beside-spotify" to ClassicBox(116f, 10f, 1284f, 606f),
            "car-1920x720-beside-spotify-tall" to ClassicBox(116f, 10f, 1284f, 682f),
            "car-1280x480-full" to ClassicBox(102f, 10f, 1268f, 446f),
            "car-1280x480-beside-media" to ClassicBox(102f, 10f, 848f, 370f),
            "car-1280x480-home-panel" to ClassicBox(792f, 10f, 1178f, 422f)
        )

    @Test
    fun `no dial text overlaps on the car's Android Auto areas`() {
        val drawer = ClassicDrawer(RuntimeEnvironment.getApplication())
        carAreas.forEach { (name, area) ->
            val plan = drawer.plan(ClassicLayout.of(area))
            val boxes = plan.boxes + plan.reserved
            boxes.forEachIndexed { i, a ->
                boxes.drop(i + 1).forEach { b -> assertFalse("$name: $a overlaps $b", a.overlaps(b)) }
            }
        }
    }

    @Test
    fun `renders the car's Android Auto areas`() {
        carAreas.forEach { (name, area) ->
            val width = if (area.right > 1280f) 1920 else 1280
            save(render(width, if (width == 1920) 720 else 480, running = true, area = area), "classic-$name.png")
        }
    }

    @Test
    fun `no dial text overlaps, from split screen to a wide screen`() {
        val drawer = ClassicDrawer(RuntimeEnvironment.getApplication())
        listOf(ClassicBox(0f, 0f, 800f, 480f), aaFull, ClassicBox(12f, 10f, 788f, 386f), aaSplit, ClassicBox(0f, 0f, 1920f, 720f)).forEach { area ->
            val plan = drawer.plan(ClassicLayout.of(area))
            val boxes = plan.boxes + plan.reserved
            boxes.forEachIndexed { i, a ->
                boxes.drop(i + 1).forEach { b -> assertFalse("${area.width} x ${area.height}: $a overlaps $b", a.overlaps(b)) }
            }
        }
    }

    @Test
    fun `split screen keeps every label, unit and readout and the scale ends`() {
        val plan = ClassicDrawer(RuntimeEnvironment.getApplication()).plan(ClassicLayout.of(aaSplit))
        val app = RuntimeEnvironment.getApplication()
        val labelOf = { slot: ClassicSlot -> plan.static.indexOfFirst { it.value in listOfNotNull(slot.labelRes, slot.shortLabelRes).map(app::getString) } }
        (CLASSIC_MAIN + CLASSIC_SUB).forEach { slot -> assertTrue("label ${app.getString(slot.labelRes)}", labelOf(slot) >= 0) }
        assertEquals("every readout has its room", 4 + 5 + 1, plan.reserved.size)
        val subLabels = CLASSIC_SUB.map { slot -> plan.boxes[labelOf(slot)] }
        subLabels.zipWithNext { a, b -> assertTrue("sub-dial labels too close: $a, $b", b.left - a.right >= 4f) }
        CLASSIC_MAIN.forEach { slot ->
            listOf(slot.scale.numerals.first(), slot.scale.numerals.last()).forEach { end ->
                assertTrue("${app.getString(slot.labelRes)} ${end.text}", plan.static.any { it.value == end.text })
            }
        }
    }

    @Test
    fun `the designed sizes leave out no numeral`() {
        val drawer = ClassicDrawer(RuntimeEnvironment.getApplication())
        listOf(ClassicBox(0f, 0f, 800f, 480f), ClassicBox(0f, 0f, 1920f, 720f)).forEach {
            val plan = drawer.plan(ClassicLayout.of(it))
            assertEquals("${it.width} x ${it.height}", emptyList<String>(), plan.dropped.map { t -> t.value })
            assertTrue("short label at ${it.width} x ${it.height}", plan.static.none { t -> t.value in listOf("48V", "GEN", "12V") })
        }
    }

    @Test
    fun `renders the Android Auto full and split screen areas`() {
        save(render(800, 480, running = true, area = aaFull), "classic-aa-full.png")
        save(render(800, 480, running = true, area = aaSplit), "classic-aa-split.png")
    }

    @Test
    fun `missing PIDs leave their slots at rest without crashing`() {
        val bitmap = render(800, 480, running = true, values = emptyMap())
        save(bitmap, "classic-800x480-no-data.png")
    }

    private fun render(
        width: Int,
        height: Int,
        running: Boolean,
        values: Map<Long, Number> = MOCKUP_VALUES,
        area: ClassicBox = ClassicBox(0f, 0f, width.toFloat(), height.toFloat())
    ): Bitmap {
        val renderer =
            ClassicSurfaceRenderer(
                context = RuntimeEnvironment.getApplication(),
                metricsCollector = FakeCollector(values),
                isRunning = { running }
            )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.onDraw(Canvas(bitmap), Rect(area.left.toInt(), area.top.toInt(), area.right.toInt(), area.bottom.toInt()))
        renderer.recycle()
        return bitmap
    }

    private fun orangePixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height step 2) {
            for (x in 0 until bitmap.width step 2) {
                val c = bitmap.getPixel(x, y)
                if (Color.red(c) > 200 && Color.green(c) in 60..140 && Color.blue(c) < 60) count++
            }
        }
        return count
    }

    private fun pixels(
        bitmap: Bitmap,
        color: Int
    ): Int {
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == color) count++
            }
        }
        return count
    }

    private fun save(
        bitmap: Bitmap,
        name: String
    ) = File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private class FakeCollector(
        values: Map<Long, Number>
    ) : MetricsCollector {
        private val metrics =
            values.mapValues { (id, value) ->
                val pid = PidDefinition(id, "00", "22", "pid $id", "22", "Codec")
                Metric.newInstance(source = ObdMetric.builder().command(ObdCommand(pid)).value(value).build(), value = value)
            }

        override fun getMetric(
            id: Long,
            enabled: Boolean
        ): Metric? = metrics[id]

        override fun getMetric(
            id: Pid,
            enabled: Boolean
        ): Metric? = metrics[id.id]

        override fun getMetrics(enabled: Boolean): List<Metric> = metrics.values.toList()

        override fun alertReset() {}

        override fun invalidate() {}

        override fun applyFilter(
            enabled: Set<Long>,
            order: Map<Long, Int>?
        ) {}

        override fun append(
            input: ObdMetric?,
            forceAppend: Boolean
        ) {}
    }
}

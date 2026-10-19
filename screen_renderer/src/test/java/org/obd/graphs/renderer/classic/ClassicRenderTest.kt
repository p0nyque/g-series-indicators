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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.Pid
import org.obd.graphs.bl.query.ClassicPids
import org.obd.graphs.renderer.api.Fps
import org.obd.metrics.api.model.ObdMetric
import org.obd.metrics.command.obd.ObdCommand
import org.obd.metrics.pid.PidDefinition
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy

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

    @Test
    fun `missing PIDs leave their slots at rest without crashing`() {
        val bitmap = render(800, 480, running = true, values = emptyMap())
        save(bitmap, "classic-800x480-no-data.png")
    }

    private fun render(
        width: Int,
        height: Int,
        running: Boolean,
        values: Map<Long, Number> = MOCKUP_VALUES
    ): Bitmap {
        val renderer =
            ClassicSurfaceRenderer(
                context = RuntimeEnvironment.getApplication(),
                settings = stub(),
                metricsCollector = FakeCollector(values),
                fps = Fps(),
                isRunning = { running }
            )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.onDraw(Canvas(bitmap), Rect(0, 0, width, height))
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

    // The renderer never reads settings (tech design D8); a proxy that throws would show it if it did.
    private inline fun <reified T> stub(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            throw UnsupportedOperationException("unexpected call ${method.name}")
        } as T

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

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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import org.obd.graphs.bl.collector.Metric
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.query.ClassicPids
import org.obd.graphs.renderer.api.SurfaceRenderer

private const val NANOS_PER_SECOND = 1_000_000_000L

// Slow slots (temperatures, levels, regen) are read every few seconds, fast ones several times a second.
private const val FAST_STALE_AFTER_NANOS = 10 * NANOS_PER_SECOND
private const val SLOW_STALE_AFTER_NANOS = 60 * NANOS_PER_SECOND

/**
 * The Classic dashboard (doc/specs/classic-dashboard.md, tech design classic-dashboard-tech.md): 11 fixed
 * indicators in the G31 Live Cockpit style. Draws by slot id, never by the collector's list, so a PID
 * injected into every query (VEHICLE_STATUS) cannot appear and a missing PID leaves its slot at rest.
 * No status panel, FPS counter or scrollbar (tech design D8); [settings] and [fps] are unused.
 */
internal class ClassicSurfaceRenderer(
    context: Context,
    private val metricsCollector: MetricsCollector,
    private val isRunning: () -> Boolean = { DataLoggerRepository.isRunning() },
    private val clock: () -> Long = System::nanoTime
) : SurfaceRenderer {
    private val drawer = ClassicDrawer(context)
    private val staticLayer = ClassicStaticLayer(drawer)
    private val fastStates = ClassicFrameStates(staleAfterNanos = FAST_STALE_AFTER_NANOS)
    private val slowStates = ClassicFrameStates(staleAfterNanos = SLOW_STALE_AFTER_NANOS)

    private var layout: ClassicLayout? = null

    override fun onDraw(
        canvas: Canvas,
        drawArea: Rect?
    ) {
        val area = if (drawArea == null || drawArea.isEmpty) Rect(0, 0, canvas.width, canvas.height) else drawArea
        val layout = layoutFor(area)
        val now = clock()
        val running = isRunning()
        if (!running) {
            fastStates.clear()
            slowStates.clear()
        }

        val baro = number(metric(ClassicPids.BARO_PRESSURE))
        val main = CLASSIC_MAIN.map { frame(it, running, baro, now) }
        val sub = CLASSIC_SUB.map { frame(it, running, baro, now) }
        val regen =
            if (running) {
                ClassicReadings.regen(number(metric(ClassicPids.DPF_REGEN_ACTIVE)), number(metric(ClassicPids.DPF_REGEN_DISTANCE)))
            } else {
                ClassicReadings.regen(null, null)
            }

        canvas.drawColor(Color.BLACK)
        staticLayer.drawUnder(canvas, layout)
        drawer.drawGlow(canvas, layout, main)
        staticLayer.drawOver(canvas, layout)
        drawer.drawValues(canvas, layout, main, sub)
        drawer.drawStatus(canvas, layout, regen)
    }

    override fun invalidate() {
        layout = null
        staticLayer.recycle()
    }

    override fun recycle() {
        invalidate()
        fastStates.clear()
        slowStates.clear()
    }

    private fun layoutFor(area: Rect): ClassicLayout {
        val box = ClassicBox(area.left.toFloat(), area.top.toFloat(), area.right.toFloat(), area.bottom.toFloat())
        return layout?.takeIf { it.area == box } ?: ClassicLayout.of(box).also { layout = it }
    }

    private fun frame(
        slot: ClassicSlot,
        running: Boolean,
        baro: Double?,
        now: Long
    ): ClassicDialFrame {
        val states = if (slot.slow) slowStates else fastStates
        val metric = metric(slot.pidId) ?: slot.fallbackPidId?.let { metric(it) }
        val raw = if (running) number(metric) else null
        val stale = metric != null && states.isStale(slot.pidId, metric.source, raw != null, now)
        val reading = ClassicReadings.reading(slot, raw, stale || !running, baro)

        val target = reading.fraction ?: ClassicReadings.restFraction(slot)
        val fraction = if (running) states.needle(slot.pidId, target, now) else target

        val targetValue =
            slot.targetPidId
                ?.takeIf { running && reading.fraction != null }
                ?.let { ClassicReadings.value(slot, number(metric(it)), baro) }

        return ClassicDialFrame(
            slot = slot,
            fraction = fraction,
            text = reading.text,
            targetFraction = targetValue?.let { slot.scale.fraction(it) },
            targetText = targetValue?.let { ClassicReadings.formatTarget(it) }
        )
    }

    private fun metric(id: Long): Metric? = metricsCollector.getMetric(id)

    private fun number(metric: Metric?): Double? = (metric?.value as? Number)?.toDouble()
}

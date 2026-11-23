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

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Bands, top to bottom (doc/specs/classic-dashboard.md, Layout).
internal const val MAIN_BAND = 0.55f
internal const val SUB_BAND = 0.32f
internal const val STRIP_BAND = 0.13f

// Tall areas: main dials 2 x 2, sub-dials 3 x 2, a thinner strip.
internal const val STACKED_MAIN_BAND = 0.55f
internal const val STACKED_SUB_BAND = 0.35f
internal const val STACKED_GAIN = 1.05f

internal const val MAIN_START_ANGLE = -120f
internal const val MAIN_SWEEP_ANGLE = 240f
internal const val SUB_START_ANGLE = -45f
internal const val SUB_SWEEP_ANGLE = 90f

// Sizes inside a dial are given for these radii and scale with the real one.
internal const val MAIN_UNIT_RADIUS = 88f
internal const val SUB_UNIT_RADIUS = 64f

internal const val MIN_TEXT_PX = 11f

/** A rectangle without android.graphics, so layout stays testable on the JVM. */
internal data class ClassicBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(other: ClassicBox) = other.left >= left && other.right <= right && other.top >= top && other.bottom <= bottom

    fun overlaps(other: ClassicBox) = left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/** A dial's centre and radius. [k] scales the spec's unit sizes to this radius. */
internal data class ClassicDialPlace(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val k: Float,
    val cell: ClassicBox,
    // Sub-dials only: baseline of the label above the arc.
    val labelY: Float = 0f
) {
    /** Everything the dial draws apart from text widths: ticks, zones, pointer and target marker. */
    fun bounds(kind: ClassicKind): ClassicBox =
        if (kind == ClassicKind.MAIN) {
            val reach = radius + 8 * k
            ClassicBox(cx - reach, cy - reach, cx + reach, cy + reach)
        } else {
            val reach = radius + 6.25f * k
            val half = reach * sin(Math.toRadians(45.0)).toFloat()
            ClassicBox(cx - half, min(labelY - 9.5f * k, cy - reach), cx + half, cy + 9 * k)
        }
}

internal data class ClassicLayout(
    val area: ClassicBox,
    // Scale of the status strip against an 800 x 480 screen.
    val textScale: Float,
    val mainBand: ClassicBox,
    val subBand: ClassicBox,
    val stripBand: ClassicBox,
    val main: List<ClassicDialPlace>,
    val sub: List<ClassicDialPlace>,
    val lamp: ClassicBox,
    // Tall areas (AA side panel): main dials 2 x 2, sub-dials in two rows of three.
    val stacked: Boolean = false
) {
    companion object {
        /**
         * One row of main dials and one of sub-dials; on a tall area (Android Auto's side panel on a widescreen)
         * main dials 2 x 2 and sub-dials 3 x 2 instead, when that makes the main dials at least
         * [STACKED_GAIN] larger (doc/specs/classic-dashboard.md, "Tall areas").
         */
        fun of(
            area: ClassicBox,
            mainCount: Int = 4,
            subCount: Int = 6
        ): ClassicLayout {
            val rows = build(area, mainCount, subCount, mainRows = 1, subRows = 1, MAIN_BAND, SUB_BAND)
            val stacked = build(area, mainCount, subCount, mainRows = 2, subRows = 2, STACKED_MAIN_BAND, STACKED_SUB_BAND)
            return if (stacked.main[0].radius > rows.main[0].radius * STACKED_GAIN) stacked.copy(stacked = true) else rows
        }

        private fun build(
            area: ClassicBox,
            mainCount: Int,
            subCount: Int,
            mainRows: Int,
            subRows: Int,
            mainShare: Float,
            subShare: Float
        ): ClassicLayout {
            val w = area.width
            val h = area.height
            val ts = min(w / 800f, h / 480f)

            val mainBand = ClassicBox(area.left, area.top, area.right, area.top + h * mainShare)
            val subBand = ClassicBox(area.left, mainBand.bottom, area.right, mainBand.bottom + h * subShare)
            val stripBand = ClassicBox(area.left, subBand.bottom, area.right, area.bottom)

            val mainCols = (mainCount + mainRows - 1) / mainRows
            val mainCell = w / mainCols
            val mainRowHeight = mainBand.height / mainRows
            val diameter = min(mainCell, mainRowHeight) * 0.94f
            val mainRadius = diameter / 2 - diameter * 0.035f
            val main =
                (0 until mainCount).map {
                    val top = mainBand.top + mainRowHeight * (it / mainCols)
                    val left = area.left + mainCell * (it % mainCols)
                    val cell = ClassicBox(left, top, left + mainCell, top + mainRowHeight)
                    ClassicDialPlace(left + mainCell / 2, top + mainRowHeight / 2 + h * 0.005f / mainRows, mainRadius, mainRadius / MAIN_UNIT_RADIUS, cell)
                }

            val subCols = (subCount + subRows - 1) / subRows
            val subCell = w / subCols
            val subRowHeight = subBand.height / subRows
            val subRadius = min(subCell * 0.48f, subRowHeight * 0.42f)
            val sub =
                (0 until subCount).map {
                    val top = subBand.top + subRowHeight * (it / subCols)
                    val left = area.left + subCell * (it % subCols)
                    val cell = ClassicBox(left, top, left + subCell, top + subRowHeight)
                    ClassicDialPlace(
                        cx = left + subCell / 2,
                        cy = top + subRowHeight * 0.2f + subRadius,
                        radius = subRadius,
                        k = subRadius / SUB_UNIT_RADIUS,
                        cell = cell,
                        labelY = top + subRowHeight * 0.11f
                    )
                }

            val lampHeight = stripBand.height * 0.55f
            val lampLeft = area.left + 24 * ts
            val lampTop = stripBand.top + (stripBand.height - lampHeight) / 2
            val lamp = ClassicBox(lampLeft, lampTop, lampLeft + lampHeight * 4.2f, lampTop + lampHeight)

            return ClassicLayout(area, ts, mainBand, subBand, stripBand, main, sub, lamp)
        }

        /** Never smaller than [MIN_TEXT_PX]. */
        fun textSize(px: Float): Float = max(px, MIN_TEXT_PX)

        /** Degrees clockwise from 12 o'clock; a null [fraction] is the slot's rest position. */
        fun angle(
            slot: ClassicSlot,
            fraction: Float?
        ): Float {
            val f = (fraction ?: ClassicReadings.restFraction(slot)).coerceIn(0f, 1f)
            return if (slot.kind == ClassicKind.MAIN) {
                MAIN_START_ANGLE + MAIN_SWEEP_ANGLE * f
            } else {
                SUB_START_ANGLE + SUB_SWEEP_ANGLE * f
            }
        }

        fun x(
            cx: Float,
            radius: Float,
            degrees: Float
        ): Float = cx + radius * sin(Math.toRadians(degrees.toDouble())).toFloat()

        fun y(
            cy: Float,
            radius: Float,
            degrees: Float
        ): Float = cy - radius * cos(Math.toRadians(degrees.toDouble())).toFloat()
    }
}

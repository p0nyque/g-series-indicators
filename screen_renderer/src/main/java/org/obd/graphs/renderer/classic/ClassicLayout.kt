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
    val lamp: ClassicBox
) {
    companion object {
        fun of(
            area: ClassicBox,
            mainCount: Int = 4,
            subCount: Int = 6
        ): ClassicLayout {
            val w = area.width
            val h = area.height
            val ts = min(w / 800f, h / 480f)

            val mainBand = ClassicBox(area.left, area.top, area.right, area.top + h * MAIN_BAND)
            val subBand = ClassicBox(area.left, mainBand.bottom, area.right, mainBand.bottom + h * SUB_BAND)
            val stripBand = ClassicBox(area.left, subBand.bottom, area.right, area.bottom)

            val mainCell = w / mainCount
            val diameter = min(mainCell, mainBand.height) * 0.94f
            val mainRadius = diameter / 2 - diameter * 0.035f
            val mainCy = mainBand.top + mainBand.height / 2 + h * 0.005f
            val main =
                (0 until mainCount).map {
                    val cell = ClassicBox(area.left + mainCell * it, mainBand.top, area.left + mainCell * (it + 1), mainBand.bottom)
                    ClassicDialPlace(cell.left + mainCell / 2, mainCy, mainRadius, mainRadius / MAIN_UNIT_RADIUS, cell)
                }

            val subCell = w / subCount
            val subRadius = min(subCell * 0.48f, subBand.height * 0.42f)
            val subCy = subBand.top + subBand.height * 0.2f + subRadius
            val sub =
                (0 until subCount).map {
                    val cell = ClassicBox(area.left + subCell * it, subBand.top, area.left + subCell * (it + 1), subBand.bottom)
                    ClassicDialPlace(
                        cx = cell.left + subCell / 2,
                        cy = subCy,
                        radius = subRadius,
                        k = subRadius / SUB_UNIT_RADIUS,
                        cell = cell,
                        labelY = subBand.top + subBand.height * 0.11f
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

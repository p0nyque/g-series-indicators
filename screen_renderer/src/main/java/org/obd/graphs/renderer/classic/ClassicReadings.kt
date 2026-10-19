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

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

internal const val NO_VALUE = "--"
internal const val NO_DISTANCE = "—"

// Used for relative boost until the first baro reading arrives.
internal const val STANDARD_BARO_KPA = 101.3

private const val MINUS = "−"

/**
 * What a slot shows this frame. [fraction] is the position on the scale (0..1, clamped), null for "at
 * rest" (no reading, stale, not connected, oil level not measured). [text] is the readout.
 */
internal data class ClassicReading(
    val fraction: Float?,
    val text: String
)

/** The status strip. [distance] is the km since the last regeneration, or [NO_DISTANCE]. */
internal data class ClassicRegen(
    val lit: Boolean,
    val distance: String
)

internal object ClassicReadings {
    /** The value the slot draws, in its unit: boost becomes relative to [baroKpa]. Null if not a number. */
    fun value(
        slot: ClassicSlot,
        raw: Double?,
        baroKpa: Double?
    ): Double? {
        val value = raw?.takeIf { it.isFinite() } ?: return null
        return if (slot.relativeToBaro) value - (baroKpa ?: STANDARD_BARO_KPA) / 100.0 else value
    }

    fun reading(
        slot: ClassicSlot,
        raw: Double?,
        stale: Boolean,
        baroKpa: Double?
    ): ClassicReading {
        val value = if (stale) null else value(slot, raw, baroKpa)
        val restText = if (slot.kind == ClassicKind.LEVEL) "" else NO_VALUE
        return when {
            value == null -> ClassicReading(null, restText)
            // Out of range = not measured (engine running, sensor still settling): pointer at rest.
            slot.kind == ClassicKind.LEVEL && (value < slot.scale.min || value > slot.scale.max) -> ClassicReading(null, "")
            else -> ClassicReading(slot.scale.fraction(value), format(slot, value))
        }
    }

    /** Where the pointer rests without a reading: the scale start, 0 for a centre-zero dial. */
    fun restFraction(slot: ClassicSlot): Float = if (slot.kind == ClassicKind.CENTRE_ZERO) slot.scale.fraction(0.0) else 0f

    fun format(
        slot: ClassicSlot,
        value: Double
    ): String {
        if (slot.kind == ClassicKind.LEVEL) return ""
        val factor = 10.0.pow(slot.decimals)
        val rounded = (value * factor).roundToLong()
        val text = String.format(Locale.ROOT, "%.${slot.decimals}f", abs(rounded) / factor)
        return when {
            rounded < 0 -> "$MINUS$text"
            rounded > 0 && slot.kind == ClassicKind.CENTRE_ZERO -> "+$text"
            else -> text
        }
    }

    /** The boost target as drawn under the readout, e.g. `1.60`. */
    fun formatTarget(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    fun regen(
        active: Double?,
        distanceKm: Double?
    ): ClassicRegen =
        ClassicRegen(
            lit = active != null && active.isFinite() && active > 0.5,
            distance = distanceKm?.takeIf { it.isFinite() && it >= 0 }?.roundToLong()?.toString() ?: NO_DISTANCE
        )
}

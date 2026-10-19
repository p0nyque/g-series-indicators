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

import org.obd.graphs.bl.query.ClassicPids
import org.obd.graphs.renderer.R

internal enum class ClassicKind {
    MAIN,
    SUB,
    CENTRE_ZERO,
    LEVEL
}

/** Printed zone colour: static styling, never an alert (doc/specs/classic-dashboard.md). */
internal enum class ClassicZoneColor {
    HOT,
    COLD
}

internal data class ClassicZone(
    val from: Double,
    val to: Double,
    val color: ClassicZoneColor
)

internal data class ClassicNumeral(
    val value: Double,
    val text: String
)

/**
 * A dial's fixed scale. [majorStep] / [minorPerMajor] lay out a main dial's ticks; sub-dials always have
 * nine ticks.
 */
internal data class ClassicScale(
    val min: Double,
    val max: Double,
    val numerals: List<ClassicNumeral>,
    val zones: List<ClassicZone> = emptyList(),
    val majorStep: Double = max - min,
    val minorPerMajor: Int = 1
) {
    fun fraction(value: Double): Float = ((value - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()

    /** Number of tick intervals on a main dial. */
    val tickSteps: Int get() = Math.round((max - min) / majorStep).toInt() * minorPerMajor
}

/**
 * One indicator slot. [fallbackPidId] is drawn when [pidId] has no metric (not in the registry).
 * [slow] slots are polled every few seconds and go stale later. [relativeToBaro]: the PID reads bar
 * absolute and the dial shows `absolute - baro`.
 */
internal data class ClassicSlot(
    val pidId: Long,
    val kind: ClassicKind,
    val labelRes: Int,
    val unit: String,
    val scale: ClassicScale,
    val decimals: Int = 0,
    val slow: Boolean = true,
    val fallbackPidId: Long? = null,
    val targetPidId: Long? = null,
    val relativeToBaro: Boolean = false
)

private const val MINUS = "−"

private fun numerals(vararg values: Int) = values.map { ClassicNumeral(it.toDouble(), it.toString().replace("-", MINUS)) }

// Main row, left to right: oil, coolant, boost, gearbox.
internal val CLASSIC_MAIN: List<ClassicSlot> =
    listOf(
        ClassicSlot(
            pidId = ClassicPids.OIL_TEMP,
            kind = ClassicKind.MAIN,
            labelRes = R.string.classic_label_oil,
            unit = "°C",
            scale =
            ClassicScale(
                min = 50.0,
                max = 150.0,
                numerals = numerals(50, 70, 90, 110, 130, 150),
                zones = listOf(ClassicZone(130.0, 150.0, ClassicZoneColor.HOT)),
                majorStep = 20.0,
                minorPerMajor = 4
            )
        ),
        ClassicSlot(
            pidId = ClassicPids.COOLANT_TEMP,
            kind = ClassicKind.MAIN,
            labelRes = R.string.classic_label_coolant,
            unit = "°C",
            scale =
            ClassicScale(
                min = 40.0,
                max = 130.0,
                numerals = numerals(40, 70, 100, 130),
                zones =
                listOf(
                    ClassicZone(40.0, 60.0, ClassicZoneColor.COLD),
                    ClassicZone(115.0, 130.0, ClassicZoneColor.HOT)
                ),
                majorStep = 30.0,
                minorPerMajor = 6
            )
        ),
        ClassicSlot(
            pidId = ClassicPids.BOOST_ACTUAL,
            fallbackPidId = ClassicPids.MAP_BOOST,
            targetPidId = ClassicPids.BOOST_TARGET,
            relativeToBaro = true,
            kind = ClassicKind.MAIN,
            labelRes = R.string.classic_label_boost,
            unit = "bar",
            decimals = 2,
            slow = false,
            scale =
            ClassicScale(
                min = 0.0,
                max = 2.0,
                numerals =
                listOf(
                    ClassicNumeral(0.0, "0"),
                    ClassicNumeral(0.5, "0.5"),
                    ClassicNumeral(1.0, "1"),
                    ClassicNumeral(1.5, "1.5"),
                    ClassicNumeral(2.0, "2")
                ),
                majorStep = 0.5,
                minorPerMajor = 5
            )
        ),
        ClassicSlot(
            pidId = ClassicPids.GEARBOX_OIL_TEMP,
            kind = ClassicKind.MAIN,
            labelRes = R.string.classic_label_gearbox,
            unit = "°C",
            scale =
            ClassicScale(
                min = 40.0,
                max = 140.0,
                numerals = numerals(40, 60, 80, 100, 120, 140),
                zones = listOf(ClassicZone(120.0, 140.0, ClassicZoneColor.HOT)),
                majorStep = 20.0,
                minorPerMajor = 4
            )
        )
    )

// Sub row, left to right: intake, EGT, oil level, 48 V, starter-generator, 12 V.
internal val CLASSIC_SUB: List<ClassicSlot> =
    listOf(
        ClassicSlot(
            pidId = ClassicPids.INTAKE_AIR_TEMP,
            kind = ClassicKind.SUB,
            labelRes = R.string.classic_label_intake,
            unit = "°C",
            scale = ClassicScale(min = -20.0, max = 80.0, numerals = numerals(-20, 30, 80))
        ),
        ClassicSlot(
            pidId = ClassicPids.EXHAUST_TEMP,
            kind = ClassicKind.SUB,
            labelRes = R.string.classic_label_egt,
            unit = "°C",
            scale =
            ClassicScale(
                min = 0.0,
                max = 900.0,
                numerals = numerals(0, 450, 900),
                zones = listOf(ClassicZone(750.0, 900.0, ClassicZoneColor.HOT))
            )
        ),
        // Scale ends = the oil level DID's raw MIN / MAX. Placeholders until found on the car (tech design Q10).
        ClassicSlot(
            pidId = ClassicPids.OIL_LEVEL,
            kind = ClassicKind.LEVEL,
            labelRes = R.string.classic_label_oil_level,
            unit = "",
            scale =
            ClassicScale(
                min = 0.0,
                max = 100.0,
                numerals = listOf(ClassicNumeral(0.0, "MIN"), ClassicNumeral(100.0, "MAX"))
            )
        ),
        ClassicSlot(
            pidId = ClassicPids.BATTERY_48V_SOC,
            kind = ClassicKind.SUB,
            labelRes = R.string.classic_label_battery_48v,
            unit = "%",
            scale = ClassicScale(min = 0.0, max = 100.0, numerals = numerals(0, 50, 100))
        ),
        ClassicSlot(
            pidId = ClassicPids.STARTER_GENERATOR_POWER,
            kind = ClassicKind.CENTRE_ZERO,
            labelRes = R.string.classic_label_starter_generator,
            unit = "kW",
            decimals = 1,
            slow = false,
            scale =
            ClassicScale(
                min = -10.0,
                max = 10.0,
                numerals =
                listOf(
                    ClassicNumeral(-10.0, "${MINUS}10"),
                    ClassicNumeral(0.0, "0"),
                    ClassicNumeral(10.0, "+10")
                )
            )
        ),
        ClassicSlot(
            pidId = ClassicPids.MODULE_VOLTAGE,
            kind = ClassicKind.SUB,
            labelRes = R.string.classic_label_battery_12v,
            unit = "V",
            decimals = 1,
            scale = ClassicScale(min = 10.0, max = 16.0, numerals = numerals(10, 13, 16))
        )
    )

/** Every PID a slot draws, the baro used for boost included. */
internal val CLASSIC_DRAWN_PIDS: Set<Long> =
    (CLASSIC_MAIN + CLASSIC_SUB)
        .flatMap { listOfNotNull(it.pidId, it.fallbackPidId, it.targetPidId) }
        .toSet() + ClassicPids.DPF_REGEN_ACTIVE + ClassicPids.DPF_REGEN_DISTANCE + ClassicPids.BARO_PRESSURE

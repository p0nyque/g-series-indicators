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
package org.obd.graphs.bl.query

/**
 * Every PID the Classic dashboard queries (doc/specs/classic-dashboard.md). The one place these ids are
 * written down: the query reads them here and the renderer's slot table refers to them.
 *
 * Mode 01 ids come from ObdMetrics' mode01.json / mode01_2.json; the 40xxx ids from bmw_b47_mhev.json, whose DIDs are
 * candidates from BMW's ECU descriptions (doc/specs/classic-dashboard-tech.md §4.3) until seen on the car.
 */
object ClassicPids {
    // OBD-II (mode 01)
    const val COOLANT_TEMP = 6L
    const val INTAKE_AIR_TEMP = 16L
    const val BARO_PRESSURE = 52L // kPa; turns absolute boost into relative boost, never drawn
    const val MODULE_VOLTAGE = 67L
    const val MAP_BOOST = 99L // bar absolute, 1 byte (max 2.55): fallback when the DDE boost PID is not loaded

    // DDE (bmw_b47_mhev.json)
    const val OIL_TEMP = 40001L
    const val BOOST_ACTUAL = 40002L // bar absolute
    const val BOOST_TARGET = 40003L // bar absolute
    const val EXHAUST_TEMP = 40004L
    const val OIL_LEVEL = 40005L
    const val STARTER_GENERATOR_POWER = 40006L // kW, + assist, - recuperation; read as 48 V battery power (BAT48)
    const val DPF_REGEN_ACTIVE = 40007L
    const val DPF_REGEN_DISTANCE = 40008L // km since the last regeneration

    // EGS
    const val GEARBOX_OIL_TEMP = 40020L

    // 48 V battery / DC-DC module
    const val BATTERY_48V_SOC = 40030L

    /**
     * Alternative DIDs for the same quantities, queried but never drawn, so one drive shows which of them answer
     * (pick them in a Trip Info / Gauge PID dialog, or read TX/RX in logcat). Remove once the real DIDs are known.
     */
    val PROBES: Set<Long> = (40101L..40113L).toSet()

    val ALL: Set<Long> =
        setOf(
            COOLANT_TEMP,
            INTAKE_AIR_TEMP,
            BARO_PRESSURE,
            MODULE_VOLTAGE,
            MAP_BOOST,
            OIL_TEMP,
            BOOST_ACTUAL,
            BOOST_TARGET,
            EXHAUST_TEMP,
            OIL_LEVEL,
            STARTER_GENERATOR_POWER,
            DPF_REGEN_ACTIVE,
            DPF_REGEN_DISTANCE,
            GEARBOX_OIL_TEMP,
            BATTERY_48V_SOC
        ) + PROBES
}

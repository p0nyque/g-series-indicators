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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.obd.graphs.bl.datalogger.JS_ENGINE_NAME
import org.obd.metrics.codec.formula.FormulaEvaluatorCodec
import org.obd.metrics.codec.formula.FormulaEvaluatorConfig
import org.obd.metrics.pid.PidDefinitionRegistry
import org.obd.metrics.pid.Resource
import org.obd.metrics.transport.message.ConnectorResponseFactory

private const val BMW_PIDS = "bmw_b47_mhev.json"

class ClassicPidsTest {
    // ObdMetrics finds PID files through the context class loader (Urls.resourceToUrl).
    private val registry: PidDefinitionRegistry =
        Thread.currentThread().contextClassLoader!!.getResourceAsStream(BMW_PIDS).use {
            PidDefinitionRegistry.builder().source(Resource.builder().inputStream(it).name(BMW_PIDS).build()).build()
        }

    private val bmwIds = ClassicPids.ALL.filter { it >= 40000 }

    @Test
    fun `the BMW PID file defines every Classic BMW PID`() {
        bmwIds.forEach { assertNotNull("PID $it", registry.findBy(it)) }
        assertEquals(bmwIds.toSet(), registry.findAll().map { it.id }.toSet())
    }

    @Test
    fun `gearbox and 48 V PIDs go to their own header table entries`() {
        assertEquals("EGS", registry.findBy(ClassicPids.GEARBOX_OIL_TEMP).overrides.canMode)
        assertEquals("BAT48", registry.findBy(ClassicPids.BATTERY_48V_SOC).overrides.canMode)
        assertEquals("22", registry.findBy(ClassicPids.OIL_TEMP).mode)
    }

    @Test
    fun `mode 22 PIDs are not batched until a batched DDE reply is seen to decode`() {
        bmwIds.forEach { assertFalse("PID $it", registry.findBy(it).overrides.isBatchEnabled) }
    }

    @Test
    fun `48 V PIDs and the starter-generator go to the 48 V battery`() {
        listOf(ClassicPids.BATTERY_48V_SOC, ClassicPids.STARTER_GENERATOR_POWER).forEach {
            assertEquals("PID $it", "BAT48", registry.findBy(it).overrides.canMode)
            assertEquals("PID $it", "D08C", registry.findBy(it).pid)
        }
    }

    @Test
    fun `formulas decode replies built from the SGBD scalings`() {
        // Reply = 62 + DID + data; the 48 V reply is the 28-byte D08C record (BATT48 RES_0xD08C_D).
        val battery48 = "12C0" + "FE0C" + "0032" + "0000".repeat(4) + "48" + "64" + "1324" + "00".repeat(11)
        mapOf(
            ClassicPids.OIL_TEMP to ("4A38" to 90.0),
            ClassicPids.BOOST_ACTUAL to ("3333" to 2.0),
            ClassicPids.BOOST_TARGET to ("5555" to 2.0),
            ClassicPids.EXHAUST_TEMP to ("8DAB" to 350.0),
            ClassicPids.OIL_LEVEL to ("80" to 37.5),
            ClassicPids.STARTER_GENERATOR_POWER to (battery48 to -2.4), // 48.00 V x -50.0 A (charging)
            ClassicPids.DPF_REGEN_ACTIVE to ("01" to 1.0),
            ClassicPids.DPF_REGEN_DISTANCE to ("0001E240" to 123.5),
            ClassicPids.GEARBOX_OIL_TEMP to ("004E" to 30.0), // a real G30 reply (OBDb)
            ClassicPids.BATTERY_48V_SOC to (battery48 to 72.0)
        ).forEach { (id, reply) ->
            assertEquals("PID $id", reply.second, decode(id, reply.first)!!.toDouble(), 0.001)
        }
    }

    @Test
    fun `every BMW PID decodes a reply of its length`() {
        bmwIds.forEach { id ->
            val value = decode(id, "40".repeat(registry.findBy(id).length))
            assertTrue("PID $id decoded $value", value != null && value.toDouble().isFinite())
        }
    }

    private fun decode(
        id: Long,
        data: String
    ): Number? {
        val pid = registry.findBy(id)
        val reply = ConnectorResponseFactory.wrap("62${pid.pid}$data".toByteArray())
        return FormulaEvaluatorCodec
            .instance(FormulaEvaluatorConfig.builder().scriptEngine(JS_ENGINE_NAME).build(), null, null)
            .decode(pid, reply)
    }

    @Test
    fun `fast PIDs have priority 0`() {
        listOf(ClassicPids.BOOST_ACTUAL, ClassicPids.BOOST_TARGET, ClassicPids.STARTER_GENERATOR_POWER).forEach {
            assertEquals("PID $it", 0, registry.findBy(it).priority)
        }
    }
}

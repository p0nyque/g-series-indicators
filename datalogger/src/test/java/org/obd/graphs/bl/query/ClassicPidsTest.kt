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
import org.junit.Test
import org.obd.metrics.pid.PidDefinitionRegistry
import org.obd.metrics.pid.Resource

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
    fun `fast PIDs have priority 0`() {
        listOf(ClassicPids.BOOST_ACTUAL, ClassicPids.BOOST_TARGET, ClassicPids.STARTER_GENERATOR_POWER).forEach {
            assertEquals("PID $it", 0, registry.findBy(it).priority)
        }
    }
}

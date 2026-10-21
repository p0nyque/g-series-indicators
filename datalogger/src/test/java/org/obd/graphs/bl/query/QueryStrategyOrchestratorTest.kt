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

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.obd.graphs.bl.TestSetup
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.DataLoggerSettings
import org.obd.graphs.bl.datalogger.Pid
import org.obd.graphs.bl.datalogger.dataLoggerSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class QueryStrategyOrchestratorTest : TestSetup() {
    private lateinit var orchestrator: QueryStrategyOrchestrator

    @Before
    override fun setup() {
        super.setup()
        mockkObject(dataLoggerSettings)
        every { dataLoggerSettings.instance() } returns DataLoggerSettings()
        // No registry in tests: the Classic strategy then keeps every slot, the MAP fallback included.
        mockkObject(DataLoggerRepository)
        every { DataLoggerRepository.getPidDefinitionRegistry() } throws IllegalStateException("no registry")
        orchestrator = QueryStrategyOrchestrator()
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `the Classic query is the only strategy and the default`() {
        assertEquals(QueryStrategyType.CLASSIC_QUERY, orchestrator.getStrategy())
        assertEquals(ClassicPids.ALL, orchestrator.getIDs())
        assertEquals(ClassicPids.ALL, orchestrator.getDefaultPIDs())
    }

    @Test
    fun `update does not change the fixed Classic query`() {
        orchestrator.update(setOf(42L))

        assertEquals(ClassicPids.ALL, orchestrator.getIDs())
    }

    @Test
    fun `getIDs adds the vehicle status pid only when disconnect-when-off is enabled`() {
        assertFalse(orchestrator.getIDs().contains(Pid.VEHICLE_STATUS_PID_ID.id))

        every { dataLoggerSettings.instance() } returns DataLoggerSettings(vehicleStatusDisconnectWhenOff = true)

        assertTrue(orchestrator.getIDs().contains(Pid.VEHICLE_STATUS_PID_ID.id))
    }
}

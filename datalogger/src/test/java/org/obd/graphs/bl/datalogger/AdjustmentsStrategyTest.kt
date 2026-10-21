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
package org.obd.graphs.bl.datalogger

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
import org.obd.graphs.bl.query.QueryStrategyType
import org.obd.metrics.pid.PIDsGroup
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AdjustmentsStrategyTest : TestSetup() {

    private val strategy = AdjustmentsStrategy()

    @Before
    override fun setup() {
        super.setup()
        mockkObject(dataLoggerSettings)
        every { context.cacheDir } returns File("/tmp")
    }

    @After
    fun tearDown() = unmockkAll()

    private fun preferences(
        stnEnabled: Boolean = false,
        metadataReadingEnabled: Boolean = true
    ) =
        DataLoggerSettings(
            debugLogging = true,
            fuelTankSize = 70,
            dumpRawConnectorResponse = true,
            adapter =
            Adapter(
                maxReconnectNum = 3,
                reconnectWhenError = true,
                batchEnabled = true,
                batchStrictValidationEnabled = true,
                calculateResponseFrames = true,
                mode01BatchSize = 10,
                otherModesBatchSize = 20,
                commandFrequency = 15,
                adaptiveConnectionEnabled = true,
                stnExtensionsEnabled = stnEnabled,
                stnIgnorePIDsPriorities = true,
                continueOnError = true,
                connectionType = "bluetooth",
                vehicleMetadataReadingEnabled = metadataReadingEnabled,
                vehicleCapabilitiesReadingEnabled = true,
                resultsCacheEnabled = true
            )
        )

    @Test
    fun `default adjustments are built from the shared preferences`() {
        val preferences = preferences()
        every { dataLoggerSettings.instance() } returns preferences

        val adjustments = strategy.findAdjustmentFor(QueryStrategyType.CLASSIC_QUERY, preferences)

        assertTrue(adjustments.isDebugEnabled)
        assertEquals(3, adjustments.errorsPolicy.numberOfRetries)
        assertTrue(adjustments.errorsPolicy.isReconnectEnabled)
        assertTrue(adjustments.errorsPolicy.isContinueOnError)
        assertTrue(adjustments.batchPolicy.isEnabled)
        assertTrue(adjustments.batchPolicy.isStrictValidationEnabled)
        assertEquals(10, adjustments.batchPolicy.mode01BatchSize)
        assertEquals(20, adjustments.batchPolicy.otherModesBatchSize)
        assertEquals(70, adjustments.formulaExternalParams.params["unit_tank_size"])
        assertTrue(adjustments.isCollectRawConnectorResponseEnabled)
        assertEquals(15L, adjustments.adaptiveTimeoutPolicy.commandFrequency)
        assertTrue(adjustments.requestedGroups.contains(PIDsGroup.METADATA))
        assertTrue(adjustments.requestedGroups.contains(PIDsGroup.CAPABILITES))
        assertFalse(adjustments.requestedGroups.contains(PIDsGroup.DTC_READ))
        assertFalse(adjustments.requestedGroups.contains(PIDsGroup.DTC_CLEAR))
    }

    @Test
    fun `no metadata and no DTC are read with metadata reading off`() {
        val preferences = preferences(metadataReadingEnabled = false)
        every { dataLoggerSettings.instance() } returns preferences

        val adjustments = strategy.findAdjustmentFor(QueryStrategyType.CLASSIC_QUERY, preferences)

        assertFalse(adjustments.requestedGroups.contains(PIDsGroup.METADATA))
        assertFalse(adjustments.isVehicleMetadataVinReadingEnabled)
        assertFalse(adjustments.requestedGroups.contains(PIDsGroup.DTC_READ))
    }
}

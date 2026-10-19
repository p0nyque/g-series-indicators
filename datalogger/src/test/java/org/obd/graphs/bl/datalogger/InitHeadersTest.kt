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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.obd.graphs.DiagnosticMappingItem

class InitHeadersTest {
    @Test
    fun `extended address and receive address reach the ObdMetrics header`() {
        val headers =
            initHeaders(
                listOf(
                    DiagnosticMappingItem(1, "22", "6F1", "DDE", extendedAddress = "12", receiveAddress = "612"),
                    DiagnosticMappingItem(2, "01", "7DF", "OBD-II")
                )
            )

        val dde = headers.single { it.mode == "22" }
        assertEquals("6F1", dde.header)
        assertEquals("12", dde.extendedAddress)
        assertEquals("612", dde.receiveAddress)
        assertTrue(dde.isExtendedAddressing)

        val obd = headers.single { it.mode == "01" }
        assertEquals("7DF", obd.header)
        assertFalse(obd.isExtendedAddressing)
        assertEquals("ATSH6F1 ATCEA12 ATCRA612 ATFCSH6F1 ATFCSD12300000 ATFCSM1", dde.commands(false).joinToString(" ") { it.query })
    }

    @Test
    fun `entries without a header are left out and the last entry of a key wins`() {
        val headers =
            initHeaders(
                listOf(
                    DiagnosticMappingItem(1, "22", "DA10F1"),
                    DiagnosticMappingItem(2, "BCM", ""),
                    DiagnosticMappingItem(3, "22", "6F1", extendedAddress = "12")
                )
            )

        assertEquals(1, headers.size)
        assertEquals("6F1", headers.single().header)
        assertEquals("12", headers.single().extendedAddress)
    }
}

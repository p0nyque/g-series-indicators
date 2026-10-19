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
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicQueryStrategyTest {
    @Test
    fun `queries every Classic PID when the DDE boost PID is not loaded`() {
        val strategy = ClassicQueryStrategy(inRegistry = { false })

        assertEquals(ClassicPids.ALL, strategy.getPIDs())
        assertEquals(ClassicPids.ALL, strategy.getDefaultPIDs())
    }

    @Test
    fun `drops the MAP boost fallback once the DDE boost PID is in the registry`() {
        val strategy = ClassicQueryStrategy(inRegistry = { it == ClassicPids.BOOST_ACTUAL })

        assertFalse(strategy.getPIDs().contains(ClassicPids.MAP_BOOST))
        assertTrue(strategy.getPIDs().contains(ClassicPids.BOOST_ACTUAL))
        assertEquals(ClassicPids.ALL - ClassicPids.MAP_BOOST, strategy.getPIDs())
    }

    @Test
    fun `update does not change the fixed query`() {
        val strategy = ClassicQueryStrategy(inRegistry = { false })

        strategy.update(setOf(1L, 2L))

        assertEquals(ClassicPids.ALL, strategy.getPIDs())
    }

    @Test
    fun `baro is queried for the boost conversion`() {
        assertTrue(ClassicPids.ALL.contains(ClassicPids.BARO_PRESSURE))
    }
}

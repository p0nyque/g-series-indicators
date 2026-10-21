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
package org.obd.graphs.aa.screen

import android.graphics.Canvas
import android.graphics.Rect
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.obd.graphs.aa.CarSettings
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.AutoConnect
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.MetricsProcessor
import org.obd.graphs.bl.datalogger.WorkflowStatus
import org.obd.graphs.preferences.setPreferencesContext
import org.obd.graphs.renderer.api.SurfaceRenderer
import org.obd.metrics.api.model.ObdMetric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClassicCarScreenTest {
    private lateinit var carContext: TestCarContext
    private var status = WorkflowStatus.Disconnected

    private val renderer =
        object : SurfaceRenderer {
            override fun onDraw(
                canvas: Canvas,
                drawArea: Rect?
            ) {}

            override fun recycle() {}

            override fun invalidate() {}
        }

    @Before
    fun setup() {
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
        setPreferencesContext(carContext)
        mockkObject(DataLoggerRepository, AutoConnect)
        every { DataLoggerRepository.status() } answers { status }
        every { DataLoggerRepository.observe(any<LifecycleOwner>(), any<(ObdMetric) -> Unit>()) } just runs
        every { DataLoggerRepository.observe(any<MetricsProcessor>()) } returns DataLoggerRepository
        every { AutoConnect.schedule(any()) } just runs
    }

    @After
    fun tearDown() = unmockkAll()

    private fun screen() = ClassicCarScreen(carContext, CarSettings(), MetricsCollector.instance(), renderer)

    @Test
    fun `the only screen is a navigation template with connect and exit`() {
        val template = screen().onGetTemplate() as NavigationTemplate

        assertEquals(2, template.actionStrip!!.actions.size)
        assertNull(template.mapActionStrip)
        assertNull(template.navigationInfo)
    }

    @Test
    fun `while connecting the template shows a loading indicator and the disconnect action`() {
        status = WorkflowStatus.Connecting

        val template = screen().onGetTemplate() as NavigationTemplate

        assertNotNull(template.navigationInfo)
        assertEquals(2, template.actionStrip!!.actions.size)
    }
}

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
package org.obd.graphs.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

// preferences.xml names custom preferences by class: a removed or renamed class fails only when the screen opens.
class PreferencesXmlTest {
    private val elements: List<Element> by lazy {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/res/xml/preferences.xml"))
        val nodes = document.getElementsByTagName("*")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun `every custom preference class exists`() {
        val missing =
            elements
                .map { it.tagName }
                .filter { it.startsWith("org.obd.graphs.") }
                .distinct()
                .filter { runCatching { Class.forName(it, false, javaClass.classLoader) }.isFailure }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `the generic ELM327 path is the only adapter path`() {
        val keys = elements.map { it.getAttributeNS(ANDROID_NS, "key") }

        assertTrue("pref.adapter.type" !in keys)
        assertTrue("pref.adapter.init.sequence" !in keys)
        assertTrue(elements.none { it.tagName.endsWith(".AdapterTypeListPreference") })
    }
}

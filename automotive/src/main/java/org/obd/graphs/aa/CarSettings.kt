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
package org.obd.graphs.aa

import org.obd.graphs.preferences.Prefs
import org.obd.graphs.preferences.getS

private const val PREF_SURFACE_FRAME_RATE = "pref.aa.surface.fps"

// 5 fps is too slow for the Classic dials' pointer easing; the BMW profile sets 15.
private const val DEFAULT_FRAME_RATE = "5"

// The Android Auto preferences the Classic screen still reads. The keys are set by vehicle profiles.
class CarSettings {
    fun getSurfaceFrameRate(): Int = Prefs.getS(PREF_SURFACE_FRAME_RATE, DEFAULT_FRAME_RATE).toInt()
}

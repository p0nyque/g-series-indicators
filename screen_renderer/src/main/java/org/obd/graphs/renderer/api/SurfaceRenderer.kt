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
package org.obd.graphs.renderer.api

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.renderer.classic.ClassicSurfaceRenderer

interface SurfaceRenderer {
    /**
     * Renders the visual content of the renderer onto the provided [canvas].
     *
     * @param canvas The target [Canvas] for drawing operations.
     * @param drawArea The specific [Rect] boundaries for rendering. If the provided area is null or empty,
     * the implementation typically defaults to the full dimensions of the canvas.
     */
    fun onDraw(
        canvas: Canvas,
        drawArea: Rect?
    )

    /**
     * Performs a "hard teardown" by releasing heavy resources to prevent memory leaks.
     */
    fun recycle()

    /**
     * Performs a "soft reset" by invalidating internal layout caches, so the next frame lays out again
     * (e.g. after the surface area changed) without a full [recycle].
     */
    fun invalidate()

    companion object {
        /** The Classic dashboard, the only screen this app draws. */
        fun classic(
            context: Context,
            metricsCollector: MetricsCollector
        ): SurfaceRenderer = ClassicSurfaceRenderer(context, metricsCollector)
    }
}

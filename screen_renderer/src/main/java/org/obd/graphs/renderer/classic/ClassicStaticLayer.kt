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
package org.obd.graphs.renderer.classic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * The parts of the Classic dashboard that do not depend on a value, drawn once per canvas size and
 * layout: [under] goes below the value glow, [over] above it. Redrawing faces with gradients, ~100 ticks
 * and the numerals at every frame would be wasted work (tech design D6).
 */
internal class ClassicStaticLayer(
    private val drawer: ClassicDrawer
) {
    private var under: Bitmap? = null
    private var over: Bitmap? = null
    private var key: Triple<Int, Int, ClassicLayout>? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun drawUnder(
        canvas: Canvas,
        layout: ClassicLayout
    ) {
        ensure(canvas, layout)
        under?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    fun drawOver(
        canvas: Canvas,
        layout: ClassicLayout
    ) {
        ensure(canvas, layout)
        over?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    fun recycle() {
        under?.recycle()
        over?.recycle()
        under = null
        over = null
        key = null
    }

    private fun ensure(
        canvas: Canvas,
        layout: ClassicLayout
    ) {
        val newKey = Triple(canvas.width, canvas.height, layout)
        if (newKey == key && under != null && over != null) return
        recycle()
        if (canvas.width <= 0 || canvas.height <= 0) return

        under = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).also { drawer.drawUnder(Canvas(it), layout) }
        over = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888).also { drawer.drawOver(Canvas(it), layout) }
        key = newKey
    }
}

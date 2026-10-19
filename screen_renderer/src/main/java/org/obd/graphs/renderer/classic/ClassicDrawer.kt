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

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import org.obd.graphs.renderer.R
import kotlin.math.cos
import kotlin.math.max

private const val LOG_TAG = "ClassicDrawer"

// Colour tokens (doc/specs/classic-dashboard.md, "Type and colour tokens").
internal object ClassicColors {
    val INK = Color.parseColor("#FFFFFF")
    val NUM = Color.parseColor("#D6D6D6")
    val DIM = Color.parseColor("#8E8E8E")
    val MINOR = Color.parseColor("#8A8A8A")
    val EDGE = Color.parseColor("#D9D9D9")
    val FACE_CENTRE = Color.parseColor("#0C0C0C")
    val FACE_MID = Color.parseColor("#141414")
    val FACE_RIM = Color.parseColor("#3A3A3A")
    val BAND_TOP = Color.parseColor("#3A3A3A")
    val BAND_BOTTOM = Color.parseColor("#1A1A1A")
    val ORANGE = Color.parseColor("#FF6A13")
    val GLOW = Color.parseColor("#FF5A00")
    const val GLOW_ALPHA = 107 // 42 %
    val RED = Color.parseColor("#A8212B")
    val BLUE = Color.parseColor("#2F7FD8")
    val ASSIST = Color.parseColor("#E2E2E2")
    val AMBER = Color.parseColor("#FFB000")
    val SEPARATOR = Color.parseColor("#2A2A2A")
    val LAMP_OFF_FILL = Color.parseColor("#101010")
    val LAMP_OFF_STROKE = Color.parseColor("#2E2E2E")
    val LAMP_OFF_TEXT = Color.parseColor("#3E3E3E")
    val LAMP_ON_TEXT = Color.parseColor("#1A1000")
}

/** One dial's state this frame, worked out by the renderer. */
internal class ClassicDialFrame(
    val slot: ClassicSlot,
    // Eased position on the scale; the rest fraction when there is no reading.
    val fraction: Float,
    val text: String,
    // Boost only: target position and text, null without a target reading.
    val targetFraction: Float? = null,
    val targetText: String? = null
)

/**
 * Canvas work of the Classic dashboard. Standalone on purpose (tech design D2): AbstractDrawer would bring
 * the colour theme and Giulia-scoped settings, and Classic ignores both.
 *
 * The static parts are drawn once into two bitmaps by [ClassicStaticLayer]: [drawUnder] (faces, sub-dial
 * bands) goes below the value glow and [drawOver] (edges, zones, ticks, numerals, labels, units) above it,
 * the order of the mockups. [drawValues] runs every frame.
 */
internal class ClassicDrawer(
    context: Context
) {
    private val regular: Typeface = font(context, "fonts/Roboto-Regular.ttf", Typeface.DEFAULT)
    private val light: Typeface = font(context, "fonts/Roboto-Light.ttf", Typeface.create("sans-serif-light", Typeface.NORMAL))
    private val bold: Typeface = Typeface.create(regular, Typeface.BOLD)

    private val labels = (CLASSIC_MAIN + CLASSIC_SUB).associate { it.labelRes to context.getString(it.labelRes) }
    private val targetLabel = context.getString(R.string.classic_target) + " "
    private val lampLabel = context.getString(R.string.classic_regen_lamp)
    private val sinceRegen = "  " + context.getString(R.string.classic_regen_since)
    private val regenerating = context.getString(R.string.classic_regen_active)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF()
    private val clip = Path()

    // ---- static: below the glow ----

    fun drawUnder(
        canvas: Canvas,
        layout: ClassicLayout
    ) {
        layout.main.forEach { place ->
            val r = place.radius + 2 * place.k
            fill.shader =
                RadialGradient(
                    place.cx,
                    place.cy,
                    r,
                    intArrayOf(ClassicColors.FACE_CENTRE, ClassicColors.FACE_MID, ClassicColors.FACE_RIM),
                    floatArrayOf(0f, 0.62f, 1f),
                    Shader.TileMode.CLAMP
                )
            canvas.drawCircle(place.cx, place.cy, r, fill)
            fill.shader = null
        }

        layout.sub.forEach { place ->
            val k = place.k
            val r = place.radius - 7 * k
            stroke.strokeWidth = 14 * k
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.shader =
                LinearGradient(
                    0f,
                    place.cy - place.radius,
                    0f,
                    place.cy - (place.radius - 14 * k) * cos(Math.toRadians(45.0)).toFloat(),
                    ClassicColors.BAND_TOP,
                    ClassicColors.BAND_BOTTOM,
                    Shader.TileMode.CLAMP
                )
            arc(canvas, place.cx, place.cy, r, SUB_START_ANGLE, SUB_START_ANGLE + SUB_SWEEP_ANGLE, stroke)
            stroke.shader = null
        }
    }

    // ---- static: above the glow ----

    fun drawOver(
        canvas: Canvas,
        layout: ClassicLayout
    ) {
        CLASSIC_MAIN.zip(layout.main).forEach { (slot, place) -> drawMainScale(canvas, slot, place) }
        CLASSIC_SUB.zip(layout.sub).forEach { (slot, place) -> drawSubScale(canvas, slot, place) }

        val ts = layout.textScale
        stroke.color = ClassicColors.SEPARATOR
        stroke.strokeWidth = 1f
        stroke.pathEffect = null
        val y = layout.stripBand.top
        canvas.drawLine(layout.area.left + 16 * ts, y, layout.area.right - 16 * ts, y, stroke)
    }

    private fun drawMainScale(
        canvas: Canvas,
        slot: ClassicSlot,
        place: ClassicDialPlace
    ) {
        val (cx, cy, r, k) = place
        val scale = slot.scale
        drawEdgeAndZones(canvas, slot, cx, cy, r + 4 * k, k)

        val steps = scale.tickSteps
        val minor = scale.minorPerMajor
        stroke.strokeCap = Paint.Cap.BUTT
        stroke.pathEffect = null
        for (i in 0..steps) {
            val d = MAIN_START_ANGLE + MAIN_SWEEP_ANGLE * i / steps
            val isMajor = i % minor == 0
            val half = minor % 2 == 0 && i % minor == minor / 2
            val length = (if (isMajor) 12f else if (half) 8f else 4.5f) * k
            stroke.color = if (isMajor || half) ClassicColors.NUM else ClassicColors.MINOR
            stroke.strokeWidth = (if (isMajor) 2.2f else 1.2f) * k
            radial(canvas, cx, cy, r - 2 * k, r - 2 * k - length, d, stroke)
        }

        scale.numerals.forEach {
            val d = ClassicLayout.angle(slot, scale.fraction(it.value))
            text(canvas, it.text, ClassicLayout.x(cx, r - 30 * k, d), ClassicLayout.y(cy, r - 30 * k, d) + 5.5f * k, 15 * k, ClassicColors.NUM, light)
        }

        text(canvas, labels.getValue(slot.labelRes), cx, cy - 22 * k, 10 * k, ClassicColors.DIM, regular, spacing = 2 * k)
        text(canvas, slot.unit, cx, cy + 32 * k, 12 * k, ClassicColors.DIM, light)
    }

    private fun drawSubScale(
        canvas: Canvas,
        slot: ClassicSlot,
        place: ClassicDialPlace
    ) {
        val (cx, cy, r, k) = place
        text(canvas, labels.getValue(slot.labelRes), cx, place.labelY, 9.5f * k, ClassicColors.DIM, regular, spacing = 1.2f * k)
        drawEdgeAndZones(canvas, slot, cx, cy, r + 2.5f * k, k)

        stroke.strokeCap = Paint.Cap.BUTT
        stroke.pathEffect = null
        for (i in 0..8) {
            val d = SUB_START_ANGLE + SUB_SWEEP_ANGLE * i / 8
            val major = i % 4 == 0
            stroke.color = if (major) ClassicColors.NUM else ClassicColors.MINOR
            stroke.strokeWidth = (if (major) 2f else 1.1f) * k
            radial(canvas, cx, cy, r - k, r - (if (major) 11f else 6f) * k, d, stroke)
        }

        slot.scale.numerals.forEach {
            val d = ClassicLayout.angle(slot, slot.scale.fraction(it.value))
            text(canvas, it.text, ClassicLayout.x(cx, r - 27 * k, d), ClassicLayout.y(cy, r - 27 * k, d) + 4 * k, 11 * k, ClassicColors.NUM, light)
        }
    }

    /** The light edge line along the sweep; printed zones replace it with a segmented band. */
    private fun drawEdgeAndZones(
        canvas: Canvas,
        slot: ClassicSlot,
        cx: Float,
        cy: Float,
        edgeRadius: Float,
        k: Float
    ) {
        var position = ClassicLayout.angle(slot, 0f)
        val end = ClassicLayout.angle(slot, 1f)
        stroke.strokeCap = Paint.Cap.BUTT
        slot.scale.zones.sortedBy { it.from }.forEach { zone ->
            val from = ClassicLayout.angle(slot, slot.scale.fraction(zone.from))
            val to = ClassicLayout.angle(slot, slot.scale.fraction(zone.to))
            edge(canvas, cx, cy, edgeRadius, position, from, k)
            stroke.color = if (zone.color == ClassicZoneColor.HOT) ClassicColors.RED else ClassicColors.BLUE
            stroke.strokeWidth = 5.5f * k
            stroke.pathEffect = DashPathEffect(floatArrayOf(9 * k, 1.6f * k), 0f)
            arc(canvas, cx, cy, edgeRadius + k, from, to, stroke)
            stroke.pathEffect = null
            position = to
        }
        edge(canvas, cx, cy, edgeRadius, position, end, k)
    }

    private fun edge(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        from: Float,
        to: Float,
        k: Float
    ) {
        stroke.color = ClassicColors.EDGE
        stroke.strokeWidth = 1.6f * k
        stroke.pathEffect = null
        arc(canvas, cx, cy, radius, from, to, stroke)
    }

    // ---- every frame ----

    /** The glow and progress lines of the main dials: drawn between the two static layers. */
    fun drawGlow(
        canvas: Canvas,
        layout: ClassicLayout,
        main: List<ClassicDialFrame>
    ) {
        glow.color = ClassicColors.GLOW
        glow.alpha = ClassicColors.GLOW_ALPHA
        glow.strokeCap = Paint.Cap.BUTT
        // sigma = 6 at 800 x 480 in the mockup; BlurMaskFilter takes a radius (sigma ~ 0.577 r + 0.5).
        glow.maskFilter = BlurMaskFilter(max(1f, 9.5f * layout.textScale), BlurMaskFilter.Blur.NORMAL)

        main.zip(layout.main).forEach { (frame, place) ->
            val (cx, cy, r, k) = place
            val to = ClassicLayout.angle(frame.slot, frame.fraction)
            if (to - MAIN_START_ANGLE < 0.01f) return@forEach

            canvas.save()
            clip.reset()
            clip.addCircle(cx, cy, r + 2 * k, Path.Direction.CW)
            canvas.clipPath(clip)
            glow.strokeWidth = 30 * k
            arc(canvas, cx, cy, r - 16 * k, MAIN_START_ANGLE, to, glow)
            canvas.restore()

            stroke.color = ClassicColors.ORANGE
            stroke.strokeWidth = 2.2f * k
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.pathEffect = null
            arc(canvas, cx, cy, r - 17 * k, MAIN_START_ANGLE, to, stroke)
        }
    }

    /** Pointers, target marker, readouts and sub-dial progress lines: drawn above everything static. */
    fun drawValues(
        canvas: Canvas,
        layout: ClassicLayout,
        main: List<ClassicDialFrame>,
        sub: List<ClassicDialFrame>
    ) {
        main.zip(layout.main).forEach { (frame, place) ->
            val (cx, cy, r, k) = place
            frame.targetFraction?.let {
                val d = ClassicLayout.angle(frame.slot, it)
                stroke.color = ClassicColors.INK
                stroke.strokeWidth = 3.2f * k
                stroke.strokeCap = Paint.Cap.BUTT
                radial(canvas, cx, cy, r + 8 * k, r - 6 * k, d, stroke)
            }
            pointer(canvas, cx, cy, r + k, r - 46 * k, ClassicLayout.angle(frame.slot, frame.fraction), 3.6f * k, layout.textScale)
            text(canvas, frame.text, cx, cy + 14 * k, 34 * k, ClassicColors.INK, regular)
            frame.targetText?.let {
                pair(canvas, targetLabel, ClassicColors.DIM, 9 * k, regular, 1.5f * k, it, ClassicColors.INK, 13 * k, light, cx, cy + 50 * k)
            }
        }

        sub.zip(layout.sub).forEach { (frame, place) ->
            val (cx, cy, r, k) = place
            val slot = frame.slot
            val rest = ClassicReadings.restFraction(slot)
            val origin = ClassicLayout.angle(slot, rest)
            val value = ClassicLayout.angle(slot, frame.fraction)
            stroke.color =
                when {
                    slot.kind != ClassicKind.CENTRE_ZERO -> ClassicColors.ORANGE
                    frame.fraction >= rest -> ClassicColors.ASSIST
                    else -> ClassicColors.BLUE
                }
            stroke.strokeWidth = 2.2f * k
            stroke.strokeCap = Paint.Cap.BUTT
            stroke.pathEffect = null
            arc(canvas, cx, cy, r - 17 * k, minOf(origin, value), maxOf(origin, value), stroke)
            pointer(canvas, cx, cy, r + 2 * k, r - 18 * k, value, 3.2f * k, layout.textScale)
            if (slot.kind != ClassicKind.LEVEL) {
                pair(canvas, frame.text, ClassicColors.INK, 19 * k, regular, 0f, " " + slot.unit, ClassicColors.DIM, 10 * k, light, cx, cy + 6 * k)
            }
        }
    }

    fun drawStatus(
        canvas: Canvas,
        layout: ClassicLayout,
        regen: ClassicRegen
    ) {
        val ts = layout.textScale
        val lamp = layout.lamp
        oval.set(lamp.left, lamp.top, lamp.right, lamp.bottom)
        val corner = 4 * ts
        val lh = lamp.height

        if (regen.lit) {
            fill.color = ClassicColors.AMBER
            fill.alpha = 128
            fill.maskFilter = BlurMaskFilter(max(1f, 6 * ts), BlurMaskFilter.Blur.NORMAL)
            canvas.drawRoundRect(oval, corner, corner, fill)
            fill.maskFilter = null
            fill.alpha = 255
            canvas.drawRoundRect(oval, corner, corner, fill)
        } else {
            fill.color = ClassicColors.LAMP_OFF_FILL
            canvas.drawRoundRect(oval, corner, corner, fill)
            stroke.color = ClassicColors.LAMP_OFF_STROKE
            stroke.strokeWidth = 1.2f
            stroke.pathEffect = null
            canvas.drawRoundRect(oval, corner, corner, stroke)
        }
        text(
            canvas,
            lampLabel,
            lamp.left + lamp.width / 2,
            lamp.top + lh * 0.68f,
            lh * 0.46f,
            if (regen.lit) ClassicColors.LAMP_ON_TEXT else ClassicColors.LAMP_OFF_TEXT,
            bold,
            spacing = 1.5f * ts
        )

        var x = lamp.right + 16 * ts
        if (regen.lit) {
            text(canvas, regenerating, x, lamp.top + lh * 0.75f, lh * 0.8f, ClassicColors.INK, light, align = Paint.Align.LEFT)
        } else {
            val baseline = lamp.top + lh * 0.78f
            x += text(canvas, regen.distance, x, baseline, lh * 0.9f, ClassicColors.INK, light, align = Paint.Align.LEFT)
            x += text(canvas, " km", x, baseline, lh * 0.55f, ClassicColors.NUM, light, align = Paint.Align.LEFT)
            text(canvas, sinceRegen, x, baseline, lh * 0.45f, ClassicColors.DIM, regular, spacing = 1.5f * ts, align = Paint.Align.LEFT)
        }
    }

    // ---- helpers ----

    /** Orange pointer bar with a soft glow, from [outer] towards the centre; no pivot. */
    private fun pointer(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
        degrees: Float,
        width: Float,
        ts: Float
    ) {
        glow.color = ClassicColors.ORANGE
        glow.alpha = 255
        glow.strokeWidth = width
        glow.strokeCap = Paint.Cap.ROUND
        glow.maskFilter = BlurMaskFilter(max(1f, 2f * ts), BlurMaskFilter.Blur.NORMAL)
        radial(canvas, cx, cy, outer, inner, degrees, glow)

        stroke.color = ClassicColors.ORANGE
        stroke.strokeWidth = width
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.pathEffect = null
        radial(canvas, cx, cy, outer, inner, degrees, stroke)
        stroke.strokeCap = Paint.Cap.BUTT
    }

    /** Arc between two angles in the spec's convention (degrees clockwise from 12 o'clock). */
    private fun arc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        from: Float,
        to: Float,
        paint: Paint
    ) {
        if (to - from < 0.01f) return
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval, from - 90f, to - from, false, paint)
    }

    private fun radial(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        outer: Float,
        inner: Float,
        degrees: Float,
        paint: Paint
    ) = canvas.drawLine(
        ClassicLayout.x(cx, outer, degrees),
        ClassicLayout.y(cy, outer, degrees),
        ClassicLayout.x(cx, inner, degrees),
        ClassicLayout.y(cy, inner, degrees),
        paint
    )

    /** Draws [value] and returns its advance. [spacing] is in px, like the mockup's letter-spacing. */
    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        px: Float,
        color: Int,
        typeface: Typeface,
        spacing: Float = 0f,
        align: Paint.Align = Paint.Align.CENTER
    ): Float {
        if (value.isEmpty()) return 0f
        style(px, color, typeface, spacing)
        text.textAlign = align
        canvas.drawText(value, x, y, text)
        return text.measureText(value)
    }

    /** Two runs centred together on [cx], e.g. readout + unit. */
    private fun pair(
        canvas: Canvas,
        first: String,
        firstColor: Int,
        firstPx: Float,
        firstFace: Typeface,
        firstSpacing: Float,
        second: String,
        secondColor: Int,
        secondPx: Float,
        secondFace: Typeface,
        cx: Float,
        y: Float
    ) {
        style(firstPx, firstColor, firstFace, firstSpacing)
        val w1 = text.measureText(first)
        style(secondPx, secondColor, secondFace, 0f)
        val w2 = text.measureText(second)
        val left = cx - (w1 + w2) / 2
        text(canvas, first, left, y, firstPx, firstColor, firstFace, firstSpacing, Paint.Align.LEFT)
        text(canvas, second, left + w1, y, secondPx, secondColor, secondFace, 0f, Paint.Align.LEFT)
    }

    private fun style(
        px: Float,
        color: Int,
        typeface: Typeface,
        spacing: Float
    ) {
        val size = ClassicLayout.textSize(px)
        text.textSize = size
        text.color = color
        text.typeface = typeface
        text.letterSpacing = spacing / size
    }

    private fun font(
        context: Context,
        asset: String,
        fallback: Typeface
    ): Typeface =
        try {
            Typeface.createFromAsset(context.assets, asset)
        } catch (e: RuntimeException) {
            Log.w(LOG_TAG, "Font $asset not bundled, using the system one")
            fallback
        }
}

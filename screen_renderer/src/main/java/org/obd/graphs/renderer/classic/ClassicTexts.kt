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

import kotlin.math.max

// Glyph box around the baseline, as a share of the text size (Roboto caps and digits are 0.71 high).
private const val ASCENT = 0.74f
private const val DESCENT = 0.06f
private const val PAD = 0.5f

// Space between the labels of neighbouring sub-dials, as a share of the text size.
internal const val LABEL_GAP = 0.5f

internal enum class ClassicFace {
    REGULAR,
    LIGHT,
    BOLD
}

internal enum class ClassicAlign {
    LEFT,
    CENTER
}

/** Width of [value] at [px] (already floored to [MIN_TEXT_PX]) with [spacing] px of letter spacing. */
internal fun interface ClassicMeasure {
    fun width(
        value: String,
        px: Float,
        face: ClassicFace,
        spacing: Float
    ): Float
}

/** One run of static text; [y] is the baseline and [px] is already floored. */
internal data class ClassicText(
    val value: String,
    val x: Float,
    val y: Float,
    val px: Float,
    val face: ClassicFace,
    val color: Int,
    val spacing: Float = 0f,
    val align: ClassicAlign = ClassicAlign.CENTER
) {
    fun box(width: Float): ClassicBox {
        val left = if (align == ClassicAlign.CENTER) x - width / 2 else x
        return ClassicBox(left - PAD, y - px * ASCENT, left + width + PAD, y + px * DESCENT)
    }
}

/**
 * The text of the static layer, fitted to the layout. [reserved] are the boxes the per-frame text
 * (readouts, boost target) can take at its widest; [dropped] are scale numerals that did not fit.
 */
internal class ClassicTextPlan(
    val static: List<ClassicText>,
    val boxes: List<ClassicBox>,
    val reserved: List<ClassicBox>,
    val dropped: List<ClassicText>
)

/**
 * Where the dial text goes (doc/specs/classic-dashboard.md, "Small areas"). Text never goes below
 * [MIN_TEXT_PX], so on a small area (Android Auto split screen) it is larger than the dial it sits in.
 * Instead of overlapping: a label's letter spacing shrinks to fit its width, and a scale numeral that
 * would touch a label, unit, readout, target or another numeral is left out, middle numerals before the
 * scale ends. At 800 x 480 nothing is left out.
 */
internal object ClassicTexts {
    fun plan(
        layout: ClassicLayout,
        labels: (ClassicSlot) -> List<String>,
        targetLabel: String,
        measure: ClassicMeasure,
        fit: Boolean = true
    ): ClassicTextPlan {
        val out = Collector(measure, fit)
        CLASSIC_MAIN.zip(layout.main).forEach { (slot, place) -> main(out.dial(), slot, place, labels(slot).first(), targetLabel) }
        CLASSIC_SUB.zip(layout.sub).forEach { (slot, place) -> sub(out.dial(), slot, place, if (fit) labels(slot) else labels(slot).take(1)) }
        return ClassicTextPlan(out.kept, out.keptBoxes, out.reserved, out.dropped)
    }

    private fun main(
        dial: Collector.Dial,
        slot: ClassicSlot,
        place: ClassicDialPlace,
        label: String,
        targetLabel: String
    ) {
        val (cx, cy, r, k) = place
        dial.fixed(text(label, cx, cy - 22 * k, 10 * k, ClassicFace.REGULAR, ClassicColors.DIM, 2 * k), maxWidth = 1.5f * r)
        dial.fixed(text(slot.unit, cx, cy + 32 * k, 12 * k, ClassicFace.LIGHT, ClassicColors.DIM))
        dial.reserve(widest(slot), cx, cy + 14 * k, 34 * k, ClassicFace.REGULAR)
        if (slot.targetPidId != null) {
            dial.reservePair(targetLabel, 9 * k, ClassicFace.REGULAR, 1.5f * k, ClassicReadings.formatTarget(slot.scale.max), 13 * k, ClassicFace.LIGHT, cx, targetY(place))
        }
        endsFirst(slot.scale.numerals).forEach {
            val d = ClassicLayout.angle(slot, slot.scale.fraction(it.value))
            val y = ClassicLayout.y(cy, r - 30 * k, d) + 5.5f * k
            dial.optional(text(it.text, ClassicLayout.x(cx, r - 30 * k, d), y, 15 * k, ClassicFace.LIGHT, ClassicColors.NUM))
        }
    }

    private fun sub(
        dial: Collector.Dial,
        slot: ClassicSlot,
        place: ClassicDialPlace,
        labels: List<String>
    ) {
        val (cx, cy, r, k) = place
        val candidates = labels.map { text(it, cx, place.labelY, 9.5f * k, ClassicFace.REGULAR, ClassicColors.DIM, 1.2f * k) }
        val maxWidth = place.cell.width - LABEL_GAP * candidates.first().px
        // The full label unless even without letter spacing it is wider than the cell, then the short one.
        dial.fixed(candidates.firstOrNull { dial.bareWidth(it) <= maxWidth } ?: candidates.last(), maxWidth)
        if (slot.kind != ClassicKind.LEVEL) {
            dial.reservePair(widest(slot), 19 * k, ClassicFace.REGULAR, 0f, " " + slot.unit, 10 * k, ClassicFace.LIGHT, cx, cy + 6 * k)
        }
        endsFirst(slot.scale.numerals).forEach {
            val d = ClassicLayout.angle(slot, slot.scale.fraction(it.value))
            val y = ClassicLayout.y(cy, r - 27 * k, d) + 4 * k
            dial.optional(text(it.text, ClassicLayout.x(cx, r - 27 * k, d), y, 11 * k, ClassicFace.LIGHT, ClassicColors.NUM))
        }
    }

    /**
     * Baseline of the boost target line: 50 units under the centre, lowered just enough to clear the scale
     * end numerals when the text floor makes it taller than designed (no change at 800 x 480).
     */
    fun targetY(place: ClassicDialPlace): Float {
        val (_, cy, r, k) = place
        val numeral = ClassicLayout.textSize(15 * k)
        val endsBottom = ClassicLayout.y(cy, r - 30 * k, MAIN_START_ANGLE + MAIN_SWEEP_ANGLE) + 5.5f * k + numeral * DESCENT + 2 * PAD
        val px = max(ClassicLayout.textSize(9 * k), ClassicLayout.textSize(13 * k))
        return max(cy + 50 * k, endsBottom + px * ASCENT + PAD)
    }

    /** Readouts a slot can show at their widest: the scale ends, a value 10 % below the scale, no value. */
    private fun widest(slot: ClassicSlot): List<String> {
        val s = slot.scale
        return listOf(s.min, s.max, s.min - (s.max - s.min) * 0.1).map { ClassicReadings.format(slot, it) } + NO_VALUE
    }

    private fun endsFirst(numerals: List<ClassicNumeral>) = numerals.sortedBy { if (it == numerals.first() || it == numerals.last()) 0 else 1 }

    private fun text(
        value: String,
        x: Float,
        y: Float,
        px: Float,
        face: ClassicFace,
        color: Int,
        spacing: Float = 0f
    ) = ClassicText(value, x, y, ClassicLayout.textSize(px), face, color, spacing)

    private class Collector(
        val measure: ClassicMeasure,
        val fit: Boolean
    ) {
        val kept = mutableListOf<ClassicText>()
        val keptBoxes = mutableListOf<ClassicBox>()
        val reserved = mutableListOf<ClassicBox>()
        val dropped = mutableListOf<ClassicText>()

        fun dial() = Dial()

        fun width(t: ClassicText) = measure.width(t.value, t.px, t.face, t.spacing)

        inner class Dial {
            private val taken = mutableListOf<ClassicBox>()

            fun bareWidth(t: ClassicText) = width(t.copy(spacing = 0f))

            /** Always drawn; with [fit], its letter spacing shrinks until it is no wider than [maxWidth]. */
            fun fixed(
                t: ClassicText,
                maxWidth: Float = Float.MAX_VALUE
            ) {
                if (t.value.isEmpty()) return
                var text = t
                var w = width(t)
                if (fit && w > maxWidth && t.spacing > 0f) {
                    val bare = width(t.copy(spacing = 0f))
                    text = t.copy(spacing = t.spacing * ((maxWidth - bare) / (w - bare)).coerceIn(0f, 1f))
                    w = width(text)
                }
                add(text, text.box(w))
            }

            /** Drawn unless it would touch something already placed in this dial. */
            fun optional(t: ClassicText) {
                val box = t.box(width(t))
                if (fit && taken.any { it.overlaps(box) }) dropped += t else add(t, box)
            }

            fun reserve(
                candidates: List<String>,
                cx: Float,
                y: Float,
                px: Float,
                face: ClassicFace
            ) {
                val size = ClassicLayout.textSize(px)
                val w = candidates.maxOf { measure.width(it, size, face, 0f) }
                block(ClassicText("", cx, y, size, face, 0).box(w))
            }

            /** Two runs centred together, e.g. readout + unit, at the widest of [firsts]. */
            fun reservePair(
                firsts: List<String>,
                firstPx: Float,
                firstFace: ClassicFace,
                firstSpacing: Float,
                second: String,
                secondPx: Float,
                secondFace: ClassicFace,
                cx: Float,
                y: Float
            ) {
                val p1 = ClassicLayout.textSize(firstPx)
                val p2 = ClassicLayout.textSize(secondPx)
                val w = firsts.maxOf { measure.width(it, p1, firstFace, firstSpacing) } + measure.width(second, p2, secondFace, 0f)
                block(ClassicText("", cx, y, max(p1, p2), firstFace, 0).box(w))
            }

            fun reservePair(
                first: String,
                firstPx: Float,
                firstFace: ClassicFace,
                firstSpacing: Float,
                second: String,
                secondPx: Float,
                secondFace: ClassicFace,
                cx: Float,
                y: Float
            ) = reservePair(listOf(first), firstPx, firstFace, firstSpacing, second, secondPx, secondFace, cx, y)

            private fun block(box: ClassicBox) {
                taken += box
                reserved += box
            }

            private fun add(
                t: ClassicText,
                box: ClassicBox
            ) {
                taken += box
                kept += t
                keptBoxes += box
            }
        }
    }
}

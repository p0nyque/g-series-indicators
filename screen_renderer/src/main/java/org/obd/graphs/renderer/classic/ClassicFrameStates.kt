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

import kotlin.math.abs
import kotlin.math.exp

private const val NANOS_PER_SECOND = 1_000_000_000L

// Low-priority PIDs are read every few seconds; a shorter limit would grey out healthy dials.
internal const val STALE_AFTER_NANOS = 10 * NANOS_PER_SECOND

// Pointer easing: an exponential approach with this time constant, frame-rate independent.
internal const val NEEDLE_TIME_CONSTANT_SECONDS = 0.12f

// After a pause longer than this (screen hidden, no frames) the pointer jumps instead of sweeping.
internal const val NEEDLE_MAX_GAP_SECONDS = 1f
private const val SNAP_DISTANCE = 0.001f

/** The pointer position to draw this frame, eased from [current] towards [target] by the time elapsed. */
internal fun easeNeedle(
    current: Float,
    target: Float,
    elapsedSeconds: Float,
    timeConstantSeconds: Float = NEEDLE_TIME_CONSTANT_SECONDS,
    maxGapSeconds: Float = NEEDLE_MAX_GAP_SECONDS
): Float {
    if (current.isNaN() || elapsedSeconds > maxGapSeconds || timeConstantSeconds <= 0f) return target
    if (elapsedSeconds <= 0f) return current
    val eased = current + (target - current) * (1f - exp(-elapsedSeconds / timeConstantSeconds))
    return if (abs(target - eased) < SNAP_DISTANCE) target else eased
}

private class ClassicFrameState {
    var needle = Float.NaN
    var lastNeedleNanos = 0L
    var lastSource: Any? = null
    var lastUpdateNanos = 0L
}

/** Per-slot cross-frame state: the eased pointer and the time of the last reading (staleness). */
internal class ClassicFrameStates(
    private val staleAfterNanos: Long = STALE_AFTER_NANOS
) {
    private val states = HashMap<Long, ClassicFrameState>()

    private fun state(
        id: Long,
        nowNanos: Long
    ): ClassicFrameState = states.getOrPut(id) { ClassicFrameState().also { it.lastUpdateNanos = nowNanos } }

    /** The needle to draw this frame, eased from the last frame's towards [target]. */
    fun needle(
        id: Long,
        target: Float,
        nowNanos: Long
    ): Float {
        val state = state(id, nowNanos)
        val elapsed = if (state.needle.isNaN()) 0f else (nowNanos - state.lastNeedleNanos).toFloat() / NANOS_PER_SECOND
        state.needle = easeNeedle(state.needle, target, elapsed)
        state.lastNeedleNanos = nowNanos
        return state.needle
    }

    /**
     * True when the PID has a value but no new reading arrived for [staleAfterNanos]. The collector
     * replaces [source] on every reading, so a change of identity is a new reading.
     */
    fun isStale(
        id: Long,
        source: Any?,
        hasValue: Boolean,
        nowNanos: Long
    ): Boolean {
        val state = state(id, nowNanos)
        if (source !== state.lastSource) {
            state.lastSource = source
            state.lastUpdateNanos = nowNanos
        }
        return hasValue && nowNanos - state.lastUpdateNanos > staleAfterNanos
    }

    fun clear() = states.clear()
}

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

import org.obd.graphs.DiagnosticMappingItem
import org.obd.metrics.api.model.Init

// Every adapter goes through the generic ELM327 path: ObdMetrics' default CAN network registry and init sequence.
// The vLinker MS preset (MS-CAN registry, HS_CAN / MS_CAN sequences) is gone; its classes were never in ObdMetrics.
fun adapterInit(
    adapter: Adapter,
    mappings: List<DiagnosticMappingItem>
): Init =
    Init
        .builder()
        .delayAfterInit(adapter.initDelay)
        .delayAfterReset(adapter.delayAfterReset)
        .headers(initHeaders(mappings).toMutableList())
        .protocol(Init.Protocol.valueOf(adapter.initProtocol))
        .build()

// The Diagnostic Request ID table as ObdMetrics' header table: one header per request key (the last entry wins,
// as the table's key/value mapping always did), entries without a header left out. An entry with an extended
// address (BMW: header 6F1, extended address 12) makes ObdMetrics send ATCEA / ATCRA / flow control with its ATSH.
fun initHeaders(mappings: List<DiagnosticMappingItem>): List<Init.Header> =
    mappings
        .filter { it.headerValue.isNotEmpty() }
        .associateBy { it.requestKey }
        .values
        .map { it.toInitHeader(it.requestKey) }

// mode is the key ObdMetrics matches: a PID's mode / CAN mode for live data, the module label for a DTC scan.
fun DiagnosticMappingItem.toInitHeader(mode: String): Init.Header =
    Init.Header
        .builder()
        .mode(mode)
        .header(headerValue)
        .extendedAddress(extendedAddress)
        .receiveAddress(receiveAddress)
        .build()

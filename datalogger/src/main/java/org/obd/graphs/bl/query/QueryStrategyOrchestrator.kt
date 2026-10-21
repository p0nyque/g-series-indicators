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

import android.util.Log
import org.obd.graphs.bl.datalogger.Pid
import org.obd.graphs.bl.datalogger.dataLoggerSettings

private const val TAG = "QueryOrchestrator"

internal class QueryStrategyOrchestrator :
    java.io.Serializable,
    Query {
    private val strategies: Map<QueryStrategyType, QueryStrategy> =
        mapOf(
            QueryStrategyType.CLASSIC_QUERY to ClassicQueryStrategy()
        )

    private var strategy: QueryStrategyType = QueryStrategyType.CLASSIC_QUERY

    override fun getDefaultPIDs(): Set<Long> = strategies[strategy]?.getDefaultPIDs() ?: emptySet()

    override fun getIDs(): MutableSet<Long> {
        val pids = strategies[strategy]?.getPIDs() ?: mutableSetOf()

        // Injected into every query: the vehicle status processor needs it to see the ignition go off.
        if (dataLoggerSettings.instance().vehicleStatusDisconnectWhenOff) {
            pids.add(Pid.VEHICLE_STATUS_PID_ID.id)
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${currentThread()} Gets PIDs '$pids' for current strategy $strategy")
        }

        return pids
    }

    override fun getStrategy(): QueryStrategyType = strategy

    override fun setStrategy(queryStrategyType: QueryStrategyType): Query {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${currentThread()} Sets new strategy $queryStrategyType")
        }
        this.strategy = queryStrategyType
        return this
    }

    override fun update(newPIDs: Set<Long>): Query {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${currentThread()} Updating query  for $strategy. New PIDs $newPIDs")
        }

        strategies[strategy]?.update(newPIDs)
        return this
    }

    private fun currentThread() = "[${Thread.currentThread().id}, ${hashCode()}]"
}

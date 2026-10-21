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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.obd.graphs.DATA_LOGGER_AUTO_CONNECT_EVENT
import org.obd.graphs.MAIN_ACTIVITY_EVENT_DESTROYED
import org.obd.graphs.MAIN_ACTIVITY_EVENT_PAUSE
import org.obd.graphs.RenderingThread
import org.obd.graphs.SCREEN_REFRESH_EVENT
import org.obd.graphs.aa.CarSettings
import org.obd.graphs.aa.R
import org.obd.graphs.aa.toast
import org.obd.graphs.bl.collector.MetricsCollector
import org.obd.graphs.bl.datalogger.AutoConnect
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ADAPTER_NOT_SET_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_BLE_NOT_REACHABLE
import org.obd.graphs.bl.datalogger.DATA_LOGGER_CONNECTED_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_CONNECTING_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ERROR_CONNECT_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ERROR_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_NO_NETWORK_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_STOPPED_EVENT
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.WORKFLOW_RELOAD_EVENT
import org.obd.graphs.bl.datalogger.WorkflowStatus
import org.obd.graphs.bl.datalogger.dataLoggerSettings
import org.obd.graphs.bl.extra.EVENT_VEHICLE_STATUS_IGNITION_OFF
import org.obd.graphs.bl.extra.vehicleStatusMetricsProcessor
import org.obd.graphs.bl.query.Query
import org.obd.graphs.bl.query.QueryStrategyType
import org.obd.graphs.profile.PROFILE_CHANGED_EVENT
import org.obd.graphs.profile.PROFILE_RESET_EVENT
import org.obd.graphs.registerReceiver
import org.obd.graphs.renderer.api.SurfaceRenderer

private const val LOG_TAG = "ClassicCarScreen"

/**
 * The only Android Auto screen: the Classic dashboard drawn on the navigation template's surface.
 * The action strip holds connect / disconnect and exit, nothing else; the dials rest while disconnected.
 */
internal class ClassicCarScreen(
    carContext: CarContext,
    private val settings: CarSettings,
    private val metricsCollector: MetricsCollector,
    surfaceRenderer: SurfaceRenderer = SurfaceRenderer.classic(carContext, metricsCollector)
) : Screen(carContext),
    DefaultLifecycleObserver {
    private val query: Query = Query.instance(QueryStrategyType.CLASSIC_QUERY)

    private val surfaceController =
        SurfaceRendererController(
            carContext,
            frameRate = { settings.getSurfaceFrameRate() },
            surfaceRenderer = surfaceRenderer
        )

    private val renderingThread =
        RenderingThread(
            id = "ClassicCarScreenRenderingThread",
            renderAction = { surfaceController.renderFrame() },
            perfFrameRate = { settings.getSurfaceFrameRate() }
        )

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Received ${intent?.action} event")
                }

                when (intent?.action) {
                    DATA_LOGGER_AUTO_CONNECT_EVENT -> {
                        if (isConnectingOrConnected()) {
                            Log.i(LOG_TAG, "Data logger is already connecting/connected. Skipping auto connect.")
                        } else {
                            Log.i(LOG_TAG, "Auto connect: starting data logging")
                            dataLoggerStart()
                        }
                    }

                    SCREEN_REFRESH_EVENT, MAIN_ACTIVITY_EVENT_DESTROYED, MAIN_ACTIVITY_EVENT_PAUSE -> invalidate()

                    SURFACE_BROKEN_EVENT -> {
                        Log.w(LOG_TAG, "Surface is broken. Closing the car app.")
                        stopRendering()
                        carContext.finishCarApp()
                    }

                    SURFACE_DESTROYED_EVENT -> stopRendering()

                    SURFACE_AREA_CHANGED_EVENT ->
                        safely("SURFACE_AREA_CHANGED_EVENT") {
                            surfaceController.invalidate()
                            invalidate()
                            startRendering()
                        }

                    DATA_LOGGER_CONNECTING_EVENT ->
                        safely("DATA_LOGGER_CONNECTING_EVENT") {
                            surfaceController.renderFrame()
                            invalidate()
                        }

                    DATA_LOGGER_CONNECTED_EVENT ->
                        safely("DATA_LOGGER_CONNECTED_EVENT") {
                            startRendering()
                            navigationManager().navigationStarted()
                            toast.show(carContext, org.obd.graphs.commons.R.string.main_activity_toast_connection_established)
                            invalidate()
                        }

                    DATA_LOGGER_STOPPED_EVENT ->
                        safely("DATA_LOGGER_STOPPED_EVENT") {
                            stopRendering()
                            invalidate()
                            surfaceController.renderFrame()
                            navigationManager().navigationEnded()
                            toast.show(carContext, org.obd.graphs.commons.R.string.main_activity_toast_connection_stopped)
                        }

                    DATA_LOGGER_NO_NETWORK_EVENT -> toastAndRefresh(org.obd.graphs.commons.R.string.main_activity_toast_connection_no_network)
                    DATA_LOGGER_ERROR_EVENT -> toastAndRefresh(org.obd.graphs.commons.R.string.main_activity_toast_connection_error)
                    DATA_LOGGER_ERROR_CONNECT_EVENT -> toastAndRefresh(org.obd.graphs.commons.R.string.main_activity_toast_connection_connect_error)
                    DATA_LOGGER_BLE_NOT_REACHABLE -> toastAndRefresh(org.obd.graphs.commons.R.string.main_activity_toast_connection_ble_not_reachable)
                    DATA_LOGGER_ADAPTER_NOT_SET_EVENT -> toastAndRefresh(org.obd.graphs.commons.R.string.main_activity_toast_adapter_is_not_selected)

                    // The PID registry was rebuilt (profile change, module list change): refresh the query.
                    PROFILE_CHANGED_EVENT, PROFILE_RESET_EVENT, WORKFLOW_RELOAD_EVENT ->
                        safely("registry reload") {
                            metricsCollector.invalidate()
                            withDataLogger { updateQuery(classicQuery()) }
                            surfaceController.invalidate()
                            surfaceController.renderFrame()
                        }

                    EVENT_VEHICLE_STATUS_IGNITION_OFF -> {
                        if (dataLoggerSettings.instance().vehicleStatusDisconnectWhenOff) {
                            Log.i(LOG_TAG, "Received vehicle status OFF event. Closing the session.")
                            withDataLogger { stop() }
                        }
                    }
                }
            }
        }

    fun onCarConfigurationChanged() = surfaceController.renderFrame()

    override fun onCreate(owner: LifecycleOwner) {
        registerReceiver(carContext, broadcastReceiver) {
            it.addAction(DATA_LOGGER_ADAPTER_NOT_SET_EVENT)
            it.addAction(DATA_LOGGER_CONNECTING_EVENT)
            it.addAction(DATA_LOGGER_STOPPED_EVENT)
            it.addAction(DATA_LOGGER_ERROR_EVENT)
            it.addAction(DATA_LOGGER_CONNECTED_EVENT)
            it.addAction(DATA_LOGGER_NO_NETWORK_EVENT)
            it.addAction(DATA_LOGGER_ERROR_CONNECT_EVENT)
            it.addAction(DATA_LOGGER_BLE_NOT_REACHABLE)
            it.addAction(DATA_LOGGER_AUTO_CONNECT_EVENT)
            it.addAction(SURFACE_DESTROYED_EVENT)
            it.addAction(SURFACE_AREA_CHANGED_EVENT)
            it.addAction(SURFACE_BROKEN_EVENT)
            it.addAction(MAIN_ACTIVITY_EVENT_DESTROYED)
            it.addAction(MAIN_ACTIVITY_EVENT_PAUSE)
            it.addAction(SCREEN_REFRESH_EVENT)
            it.addAction(PROFILE_CHANGED_EVENT)
            it.addAction(PROFILE_RESET_EVENT)
            it.addAction(WORKFLOW_RELOAD_EVENT)
            it.addAction(EVENT_VEHICLE_STATUS_IGNITION_OFF)
        }

        withDataLogger { updateQuery(classicQuery()) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopRendering()
        carContext.unregisterReceiver(broadcastReceiver)
    }

    override fun onGetTemplate(): Template =
        try {
            if (DataLoggerRepository.status() == WorkflowStatus.Connecting) {
                NavigationTemplate
                    .Builder()
                    .setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
                    .setActionStrip(actionStrip())
                    .build()
            } else {
                NavigationTemplate.Builder().setActionStrip(actionStrip()).build()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to build template", e)
            PaneTemplate
                .Builder(Pane.Builder().setLoading(true).build())
                .setHeaderAction(Action.BACK)
                .setTitle(carContext.getString(R.string.pref_aa_car_error))
                .build()
        }

    private fun actionStrip(): ActionStrip {
        val builder = ActionStrip.Builder()
        if (isConnectingOrConnected()) {
            builder.addAction(
                createAction(carContext, R.drawable.action_disconnect, CarColor.RED) {
                    Log.i(LOG_TAG, "Stopping data logging process")
                    withDataLogger { stop() }
                    stopRendering()
                    toast.show(carContext, R.string.toast_connection_disconnect)
                }
            )
        } else {
            builder.addAction(
                createAction(carContext, R.drawable.actions_connect, CarColor.GREEN) {
                    dataLoggerStart()
                }
            )
        }
        builder.addAction(
            createAction(carContext, R.drawable.action_exit, CarColor.RED) {
                try {
                    withDataLogger { stop() }
                    stopRendering()
                } finally {
                    Log.i(LOG_TAG, "Exiting the app. Closing the context")
                    carContext.finishCarApp()
                }
            }
        )
        return builder.build()
    }

    private fun dataLoggerStart() {
        withDataLogger { start(classicQuery()) }
    }

    // The Classic screen always queries its fixed slots; the collector shows the same set.
    private fun classicQuery(): Query {
        query.setStrategy(QueryStrategyType.CLASSIC_QUERY)
        metricsCollector.applyFilter(enabled = query.getIDs())
        return query
    }

    private fun startRendering() {
        if (!renderingThread.isRunning() && DataLoggerRepository.status() == WorkflowStatus.Connected) {
            renderingThread.start()
        }
    }

    private fun stopRendering() = renderingThread.stop()

    private fun isConnectingOrConnected() =
        DataLoggerRepository.status() == WorkflowStatus.Connecting || DataLoggerRepository.status() == WorkflowStatus.Connected

    private fun toastAndRefresh(message: Int) =
        safely("toast") {
            invalidate()
            toast.show(carContext, message)
        }

    private inline fun safely(
        what: String,
        action: () -> Unit
    ) {
        try {
            action()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed when handling $what", e)
        }
    }

    private fun navigationManager() = carContext.getCarService(NavigationManager::class.java)

    init {
        lifecycle.addObserver(this)
        lifecycle.addObserver(surfaceController)

        DataLoggerRepository.observe(this) {
            metricsCollector.append(it, forceAppend = false)
        }
        DataLoggerRepository.observe(vehicleStatusMetricsProcessor)

        startRendering()
        AutoConnect.schedule(carContext)

        navigationManager().setNavigationManagerCallback(
            object : NavigationManagerCallback {
                override fun onStopNavigation() {
                    try {
                        stopRendering()
                        surfaceController.renderFrame()
                        invalidate()
                    } catch (e: Throwable) {
                        Log.e(LOG_TAG, "Failed to stop rendering", e)
                    }
                }
            }
        )
    }
}

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
package org.obd.graphs.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.obd.graphs.BuildConfig
import org.obd.graphs.ExceptionHandler
import org.obd.graphs.MAIN_ACTIVITY_EVENT_DESTROYED
import org.obd.graphs.MAIN_ACTIVITY_EVENT_PAUSE
import org.obd.graphs.MODULES_LIST_CHANGED_EVENT
import org.obd.graphs.Network
import org.obd.graphs.Permissions
import org.obd.graphs.R
import org.obd.graphs.REQUEST_LOCATION_PERMISSIONS
import org.obd.graphs.REQUEST_NOTIFICATION_PERMISSIONS
import org.obd.graphs.REQUEST_PERMISSIONS_BT
import org.obd.graphs.SCREEN_LOCK_DIALOG_CANCELLED_EVENT
import org.obd.graphs.SCREEN_LOCK_PROGRESS_EVENT
import org.obd.graphs.SCREEN_UNLOCK_PROGRESS_EVENT
import org.obd.graphs.ScreenLock
import org.obd.graphs.bl.datalogger.AutoConnect
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ADAPTER_NOT_SET_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_BLE_NOT_REACHABLE
import org.obd.graphs.bl.datalogger.DATA_LOGGER_CONNECTED_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_CONNECTING_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ERROR_CONNECT_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_ERROR_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_NO_NETWORK_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_STOPPED_EVENT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_WIFI_INCORRECT
import org.obd.graphs.bl.datalogger.DATA_LOGGER_WIFI_NOT_CONNECTED
import org.obd.graphs.bl.datalogger.DataLoggerRepository
import org.obd.graphs.bl.datalogger.WorkflowStatus
import org.obd.graphs.bl.datalogger.dataLoggerSettings
import org.obd.graphs.bl.extra.EVENT_VEHICLE_STATUS_IGNITION_OFF
import org.obd.graphs.bl.extra.vehicleStatusMetricsProcessor
import org.obd.graphs.bl.query.Query
import org.obd.graphs.getContext
import org.obd.graphs.getSerializableCompat
import org.obd.graphs.preferences.PREFERENCE_SCREEN_KEY
import org.obd.graphs.preferences.PREFS_CONNECTION_TYPE_CHANGED_EVENT
import org.obd.graphs.preferences.PreferencesFragment
import org.obd.graphs.preferences.setPreferencesContext
import org.obd.graphs.profile.PROFILE_CHANGED_EVENT
import org.obd.graphs.profile.profile
import org.obd.graphs.registerReceiver
import org.obd.graphs.sendBroadcastEvent
import org.obd.graphs.setActivityContext
import org.obd.graphs.ui.common.toast
import org.obd.graphs.ui.withDataLogger
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions

const val LOG_TAG = "MainActivity"

/**
 * The phone app is a settings screen: adapter connection, PID registry and vehicle profile. The dashboard
 * itself is drawn on Android Auto. The floating button connects to the adapter, which is how the connection
 * settings are tried out without a head unit.
 */
class MainActivity :
    AppCompatActivity(),
    EasyPermissions.PermissionCallbacks,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    internal val screenLockManager = ScreenLockManager(this)

    private val connectButton: FloatingActionButton by lazy { findViewById(R.id.connect_btn) }

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                receive(intent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setActivityContext(this)
        setPreferencesContext(this)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        screenLockManager.setup()
        lifecycle.addObserver(screenLockManager)
        registerReceivers()

        if (savedInstanceState == null) {
            Thread.setDefaultUncaughtExceptionHandler(ExceptionHandler())
            profile.init(
                versionCode = BuildConfig.VERSION_CODE,
                defaultProfile = resources.getString(R.string.DEFAULT_PROFILE),
                versionName = BuildConfig.VERSION_NAME
            )
            profile.setupProfiles(forceOverrideRecommendation = false)
            Network.setupConnectedNetworksCallback()
            DataLoggerRepository.observe(vehicleStatusMetricsProcessor)
            AutoConnect.schedule(this)
            showPreferences(null)
        }

        connectButton.setOnClickListener { toggleConnection() }
        supportFragmentManager.addOnBackStackChangedListener { updateStatus() }
        updateStatus()

        if (Permissions.isAnyPermissionMissing(this)) {
            Permissions.showPermissionOnboarding(this)
        }
    }

    override fun onPause() {
        super.onPause()
        sendBroadcastEvent(MAIN_ACTIVITY_EVENT_PAUSE)
    }

    override fun onDestroy() {
        unregisterReceiver(broadcastReceiver)
        unregisterReceiver(DataLoggerRepository.broadcastReceivers())
        sendBroadcastEvent(MAIN_ACTIVITY_EVENT_DESTROYED)
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        return super.onSupportNavigateUp()
    }

    // A preference with app:fragment (the CAN header table) opens that fragment in place of the settings.
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(classLoader, pref.fragment ?: return false)
        fragment.arguments = pref.extras
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(pref.key)
            .commit()
        return true
    }

    /** Shows the settings screen with the given root key (null = the top level). Showing the current screen again reloads it. */
    fun showPreferences(rootKey: String?) {
        val current = supportFragmentManager.findFragmentById(R.id.settings_container)
        val sameScreen = current is PreferencesFragment && current.arguments?.getString(PREFERENCE_SCREEN_KEY) == rootKey
        val fragment = PreferencesFragment().apply { arguments = bundleOf(PREFERENCE_SCREEN_KEY to rootKey) }
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .apply { if (rootKey != null && !sameScreen) addToBackStack(rootKey) }
            .commit()
    }

    private fun toggleConnection() {
        if (DataLoggerRepository.status() == WorkflowStatus.Connecting || DataLoggerRepository.status() == WorkflowStatus.Connected) {
            Log.i(LOG_TAG, "Stopping data logging")
            withDataLogger { stop() }
        } else {
            Log.i(LOG_TAG, "Starting data logging")
            withDataLogger { start(Query.instance()) }
        }
    }

    private fun updateStatus() {
        val settings = dataLoggerSettings.instance().adapter
        val state =
            when (DataLoggerRepository.status()) {
                WorkflowStatus.Connecting -> getString(R.string.main_activity_status_connecting)
                WorkflowStatus.Connected -> getString(R.string.main_activity_status_connected)
                else -> getString(R.string.main_activity_status_disconnected)
            }
        supportActionBar?.subtitle = "${settings.connectionType} · ${profile.getCurrentProfileName().substringBefore("(").trim()} · $state"
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)

        val connected = DataLoggerRepository.status() != WorkflowStatus.Disconnected
        connectButton.backgroundTintList =
            ContextCompat.getColorStateList(
                this,
                if (connected) org.obd.graphs.commons.R.color.cardinal else org.obd.graphs.commons.R.color.philippine_green
            )
        connectButton.contentDescription =
            getString(if (connected) R.string.main_activity_disconnect else R.string.main_activity_connect)
    }

    private fun receive(intent: Intent?) {
        when (intent?.action) {
            DATA_LOGGER_CONNECTING_EVENT -> updateStatus()
            DATA_LOGGER_CONNECTED_EVENT -> {
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_established)
                updateStatus()
            }
            DATA_LOGGER_STOPPED_EVENT -> {
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_stopped)
                updateStatus()
            }
            DATA_LOGGER_ERROR_EVENT -> {
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_error)
                updateStatus()
            }
            DATA_LOGGER_NO_NETWORK_EVENT -> {
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_no_network)
                updateStatus()
            }
            DATA_LOGGER_ERROR_CONNECT_EVENT -> toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_connect_error)
            DATA_LOGGER_BLE_NOT_REACHABLE -> toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_ble_not_reachable)
            DATA_LOGGER_ADAPTER_NOT_SET_EVENT -> {
                screenLockManager.dismiss()
                showPreferences(PREFERENCE_SCREEN_KEY_CONNECTION)
                toast(org.obd.graphs.commons.R.string.main_activity_toast_adapter_is_not_selected)
            }
            DATA_LOGGER_WIFI_NOT_CONNECTED -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_wifi_not_connected)
            }
            DATA_LOGGER_WIFI_INCORRECT -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                toast(org.obd.graphs.commons.R.string.main_activity_toast_connection_wifi_incorrect_ssid)
            }

            SCREEN_UNLOCK_PROGRESS_EVENT -> screenLockManager.dismiss()
            SCREEN_LOCK_PROGRESS_EVENT -> {
                val screenLock = intent.getSerializableCompat<ScreenLock>() ?: ScreenLock()
                screenLockManager.show(screenLock) { sendBroadcastEvent(SCREEN_LOCK_DIALOG_CANCELLED_EVENT, screenLock) }
            }

            REQUEST_PERMISSIONS_BT -> Permissions.requestBluetoothPermissions(this)
            REQUEST_NOTIFICATION_PERMISSIONS -> Permissions.requestNotificationPermissions(this)
            REQUEST_LOCATION_PERMISSIONS -> Permissions.requestLocationPermissions(this)

            PREFS_CONNECTION_TYPE_CHANGED_EVENT, PROFILE_CHANGED_EVENT -> updateStatus()

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                (intent.extras?.get(UsbManager.EXTRA_DEVICE) as? UsbDevice)?.let { toast(R.string.pref_usb_device_detached, it.productName ?: "") }
                withDataLogger { stop() }
            }
            USB_DEVICE_ATTACHED_EVENT -> {
                (intent.extras?.get(UsbManager.EXTRA_DEVICE) as? UsbDevice)?.let { toast(R.string.pref_usb_device_attached, it.productName ?: "") }
            }

            EVENT_VEHICLE_STATUS_IGNITION_OFF -> {
                if (dataLoggerSettings.instance().vehicleStatusDisconnectWhenOff) {
                    Log.i(LOG_TAG, "Received vehicle status OFF event. Closing the session.")
                    withDataLogger { stop() }
                }
            }
        }
    }

    private fun registerReceivers() {
        registerReceiver(this, broadcastReceiver) {
            it.addAction(DATA_LOGGER_CONNECTING_EVENT)
            it.addAction(DATA_LOGGER_CONNECTED_EVENT)
            it.addAction(DATA_LOGGER_STOPPED_EVENT)
            it.addAction(DATA_LOGGER_ERROR_EVENT)
            it.addAction(DATA_LOGGER_NO_NETWORK_EVENT)
            it.addAction(DATA_LOGGER_ERROR_CONNECT_EVENT)
            it.addAction(DATA_LOGGER_BLE_NOT_REACHABLE)
            it.addAction(DATA_LOGGER_ADAPTER_NOT_SET_EVENT)
            it.addAction(DATA_LOGGER_WIFI_NOT_CONNECTED)
            it.addAction(DATA_LOGGER_WIFI_INCORRECT)
            it.addAction(SCREEN_LOCK_PROGRESS_EVENT)
            it.addAction(SCREEN_UNLOCK_PROGRESS_EVENT)
            it.addAction(REQUEST_PERMISSIONS_BT)
            it.addAction(REQUEST_NOTIFICATION_PERMISSIONS)
            it.addAction(REQUEST_LOCATION_PERMISSIONS)
            it.addAction(PREFS_CONNECTION_TYPE_CHANGED_EVENT)
            it.addAction(PROFILE_CHANGED_EVENT)
            it.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            it.addAction(USB_DEVICE_ATTACHED_EVENT)
            it.addAction(EVENT_VEHICLE_STATUS_IGNITION_OFF)
        }

        // Reloads the PID registry when the profile or the module list changes.
        registerReceiver(this, DataLoggerRepository.broadcastReceivers()) {
            it.addAction(MODULES_LIST_CHANGED_EVENT)
            it.addAction(PROFILE_CHANGED_EVENT)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(
        requestCode: Int,
        perms: MutableList<String>
    ) {
    }

    override fun onPermissionsDenied(
        requestCode: Int,
        perms: MutableList<String>
    ) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }
}

const val PREFERENCE_SCREEN_KEY_CONNECTION = "pref.adapter.connection"

/** Opens (or reloads) the settings screen with the given root key. Preferences call it after a change to refresh their screen. */
fun navigateToPreferencesScreen(navigateToPrefKey: String) {
    (getContext() as? MainActivity)?.showPreferences(navigateToPrefKey)
}

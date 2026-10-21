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
package org.obd.graphs.preferences

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import org.obd.graphs.R
import org.obd.graphs.activity.MainActivity
import org.obd.graphs.preferences.pid.PidDefinitionDialogFragment
import org.obd.graphs.preferences.pid.PidDefinitionListPreferences
import org.obd.graphs.sendBroadcastEvent

const val PREFERENCE_SCREEN_KEY = "preferences.rootKey"
const val PREFS_CONNECTION_TYPE_CHANGED_EVENT = "prefs.connection_type.changed.event"
const val PREFERENCE_CONNECTION_TYPE = "pref.adapter.connection.type"

// One screen of preferences.xml, chosen by the PREFERENCE_SCREEN_KEY argument (null = the top level).
class PreferencesFragment : PreferenceFragmentCompat() {
    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is PidDefinitionListPreferences ->
                PidDefinitionDialogFragment(key = preference.key, source = preference.source).show(parentFragmentManager, null)

            else -> super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onNavigateToScreen(preferenceScreen: PreferenceScreen) {
        (activity as? MainActivity)?.showPreferences(preferenceScreen.key)
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.preferences, arguments?.getString(PREFERENCE_SCREEN_KEY) ?: rootKey)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        registerConnectionTypeListener()
        customizeListView()
        return root
    }

    private fun customizeListView() {
        val typedValue = TypedValue()
        if (requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)) {
            listView.setBackgroundColor(typedValue.data)
        } else {
            listView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // Narrows the visible sub-category to the currently selected connection type.
    private fun registerConnectionTypeListener() {
        val connectionType = findPreference<ListPreference>(PREFERENCE_CONNECTION_TYPE)
        val bluetoothCategory = findPreference<Preference>("$PREFERENCE_CONNECTION_TYPE.bluetooth")
        val bleCategory = findPreference<Preference>("$PREFERENCE_CONNECTION_TYPE.ble")
        val wifiCategory = findPreference<Preference>("$PREFERENCE_CONNECTION_TYPE.wifi")
        val usbCategory = findPreference<Preference>("$PREFERENCE_CONNECTION_TYPE.usb")

        fun applyVisibility(type: String?) {
            bluetoothCategory?.isVisible = type == "bluetooth"
            bleCategory?.isVisible = type == "ble"
            wifiCategory?.isVisible = type == "wifi"
            usbCategory?.isVisible = type == "usb"
        }

        applyVisibility(Prefs.getString(PREFERENCE_CONNECTION_TYPE))

        connectionType?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                sendBroadcastEvent(PREFS_CONNECTION_TYPE_CHANGED_EVENT)
                applyVisibility(newValue as? String)
                true
            }
    }
}

package com.hamham.gpsmover.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.hamham.gpsmover.R
import com.hamham.gpsmover.utils.PrefManager
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDataStore
import androidx.appcompat.app.AppCompatDelegate
import rikka.preference.SimpleMenuPreference
import androidx.preference.Preference

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = object : PreferenceDataStore() {
            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                return when (key) {
                    "isHookedSystem" -> PrefManager.isHookSystem
                    "random_position" -> PrefManager.isRandomPosition
                    "disable_update" -> PrefManager.disableUpdate
                    else -> throw IllegalArgumentException("Invalid key $key")
                }
            }
            override fun putBoolean(key: String?, value: Boolean) {
                when (key) {
                    "isHookedSystem" -> PrefManager.isHookSystem = value
                    "random_position" -> PrefManager.isRandomPosition = value
                    "disable_update" -> PrefManager.disableUpdate = value
                    else -> throw IllegalArgumentException("Invalid key $key")
                }
            }
            override fun getString(key: String?, defValue: String?): String? {
                return when (key) {
                    "accuracy_settings" -> PrefManager.accuracy
                    "random_position_range" -> PrefManager.randomPositionRange
                    "map_type" -> PrefManager.mapType.toString()
                    "darkTheme" -> PrefManager.darkTheme.toString()
                    else -> throw IllegalArgumentException("Invalid key $key")
                }
            }
            override fun putString(key: String?, value: String?) {
                when (key) {
                    "accuracy_settings" -> PrefManager.accuracy = value
                    "random_position_range" -> PrefManager.randomPositionRange = value
                    "map_type" -> PrefManager.mapType = value!!.toInt()
                    "darkTheme" -> PrefManager.darkTheme = value!!.toInt()
                    else -> throw IllegalArgumentException("Invalid key $key")
                }
            }
        }
        setPreferencesFromResource(R.xml.setting, rootKey)

        findPreference<EditTextPreference>("accuracy_settings")?.let {
            it.summary = "${PrefManager.accuracy} m."
            it.setOnBindEditTextListener { editText ->
                editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                editText.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
                editText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val newText = s?.toString()?.replace(",", ".")
                        if (newText != s?.toString()) {
                            editText.setText(newText)
                            editText.setSelection(newText?.length ?: 0)
                        }
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
            it.setOnPreferenceChangeListener { preference, newValue ->
                try {
                    newValue as String?
                    preference.summary = "$newValue  m."
                } catch (n: NumberFormatException) {
                    n.printStackTrace()
                    android.widget.Toast.makeText(requireContext(), getString(R.string.enter_valid_input), android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        findPreference<Preference>("random_position_range")?.let {
            it.summary = "${PrefManager.randomPositionRange} m."
            it.setOnPreferenceClickListener { _ ->
                showRandomPositionDialog()
                true
            }
        }
        findPreference<SimpleMenuPreference>("darkTheme")?.setOnPreferenceChangeListener { _, newValue ->
            val newMode = (newValue as String).toInt()
            if (PrefManager.darkTheme != newMode) {
                AppCompatDelegate.setDefaultNightMode(newMode)
                activity?.recreate()
            }
            true
        }
    }

            private fun showRandomPositionDialog() {
            val dialogView = layoutInflater.inflate(R.layout.dialog_random_position, null)
            val switchView = dialogView.findViewById<android.widget.Switch>(R.id.random_position_switch)
            val editText = dialogView.findViewById<android.widget.EditText>(R.id.random_position_edit)

            // Set current values
            switchView.isChecked = PrefManager.isRandomPosition
            val currentValue = PrefManager.randomPositionRange?.ifEmpty { "2" } ?: "2"
            editText.setText(currentValue)

            // Handle switch changes
            switchView.setOnCheckedChangeListener { _, isChecked ->
                PrefManager.isRandomPosition = isChecked
            }

        // Handle text input
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
        editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString()?.replace(",", ".")
                if (newText != s?.toString()) {
                    editText.setText(newText)
                    editText.setSelection(newText?.length ?: 0)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

                    val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("OK") { _, _ ->
                    try {
                        val newValue = editText.text.toString()
                        if (newValue.isNotEmpty()) {
                            PrefManager.randomPositionRange = newValue
                            findPreference<Preference>("random_position_range")?.summary = "$newValue m."
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.enter_valid_input), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()
        }
} 
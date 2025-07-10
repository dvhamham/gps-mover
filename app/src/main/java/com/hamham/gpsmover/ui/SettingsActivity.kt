package com.hamham.gpsmover.ui


import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.hamham.gpsmover.R
import com.hamham.gpsmover.databinding.SettingsActivityBinding
import com.hamham.gpsmover.utils.PrefManager
import rikka.preference.SimpleMenuPreference


class SettingsActivity : AppCompatActivity() {

    private val binding by lazy {
        SettingsActivityBinding.inflate(layoutInflater)
    }

    class SettingPreferenceDataStore() : PreferenceDataStore() {
        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return when (key) {
                "isHookedSystem" -> PrefManager.isHookSystem
                "random_position" -> PrefManager.isRandomPosition
                "disable_update" -> PrefManager.disableUpdate
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }

        override fun putBoolean(key: String?, value: Boolean) {
            return when (key) {
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
            return when (key) {
                "accuracy_settings" -> PrefManager.accuracy = value
                "random_position_range" -> PrefManager.randomPositionRange = value
                "map_type" -> PrefManager.mapType = value!!.toInt()
                "darkTheme" -> PrefManager.darkTheme = value!!.toInt()
                else -> throw IllegalArgumentException("Invalid key $key")
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsPreferenceFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }


    class SettingsPreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager?.preferenceDataStore = SettingPreferenceDataStore()
            setPreferencesFromResource(R.xml.setting, rootKey)

            findPreference<EditTextPreference>("accuracy_settings")?.let {
                it.summary = "${PrefManager.accuracy} m."
                it.setOnBindEditTextListener { editText ->
                    editText.inputType = InputType.TYPE_CLASS_NUMBER;
                    editText.keyListener = DigitsKeyListener.getInstance("0123456789.,");
                    editText.addTextChangedListener(getCommaReplacerTextWatcher(editText));
                }

                it.setOnPreferenceChangeListener { preference, newValue ->
                    try {
                        newValue as String?
                        preference.summary = "$newValue  m."
                    } catch (n: NumberFormatException) {
                        n.printStackTrace()
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.enter_valid_input),
                            Toast.LENGTH_SHORT
                        ).show()
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
                        Toast.makeText(requireContext(), getString(R.string.enter_valid_input), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()
        }

        private fun getCommaReplacerTextWatcher(editText: EditText): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun onTextChanged(
                    charSequence: CharSequence,
                    i: Int,
                    i1: Int,
                    i2: Int
                ) {
                }

                override fun afterTextChanged(editable: Editable) {
                    val text = editable.toString()
                    if (text.contains(",")) {
                        editText.setText(text.replace(",", "."))
                        editText.setSelection(editText.text.length)
                    }
                }
            }
        }

    }


}
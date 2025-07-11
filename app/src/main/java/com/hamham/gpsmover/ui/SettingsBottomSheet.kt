package com.hamham.gpsmover.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.hamham.gpsmover.R
import com.hamham.gpsmover.utils.PrefManager

class SettingsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        updateSummaries()
    }

    private fun setupViews(view: View) {
        // Dark Theme
        view.findViewById<MaterialButton>(R.id.dark_theme_button).setOnClickListener {
            showDarkThemeDialog()
        }

        // Map Type
        view.findViewById<MaterialButton>(R.id.map_type_button).setOnClickListener {
            showMapTypeDialog()
        }

        // Advanced Hook Switch
        view.findViewById<SwitchMaterial>(R.id.advance_hook_switch).apply {
            isChecked = PrefManager.isHookSystem
            setOnCheckedChangeListener { _, isChecked ->
                PrefManager.isHookSystem = isChecked
            }
        }

        // Accuracy Settings
        view.findViewById<MaterialButton>(R.id.accuracy_button).setOnClickListener {
            showAccuracyDialog()
        }

        // Random Position
        view.findViewById<MaterialButton>(R.id.random_position_button).setOnClickListener {
            showRandomPositionDialog()
        }
    }

    private fun updateSummaries() {
        view?.let { view ->
            // Dark Theme Summary
            val darkThemeSummary = when (PrefManager.darkTheme) {
                AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
                AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
                else -> getString(R.string.system)
            }
            view.findViewById<TextView>(R.id.dark_theme_summary).text = darkThemeSummary

            // Map Type Summary
            val mapTypeSummary = when (PrefManager.mapType) {
                1 -> "Normal"
                2 -> "Satellite"
                3 -> "Terrain"
                else -> "Normal"
            }
            view.findViewById<TextView>(R.id.map_type_summary).text = mapTypeSummary

            // Accuracy Summary
            view.findViewById<TextView>(R.id.accuracy_summary).text = "${PrefManager.accuracy} m."

            // Random Position Summary
            val randomSummary = if (PrefManager.isRandomPosition) {
                "${PrefManager.randomPositionRange} m."
            } else {
                "Disabled"
            }
            view.findViewById<TextView>(R.id.random_position_summary).text = randomSummary
        }
    }

    private fun showDarkThemeDialog() {
        val themes = arrayOf(getString(R.string.system), getString(R.string.light), getString(R.string.dark))
        val currentIndex = when (PrefManager.darkTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dark_theme))
            .setSingleChoiceItems(themes, currentIndex) { dialog, which ->
                val newMode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (PrefManager.darkTheme != newMode) {
                    PrefManager.darkTheme = newMode
                    AppCompatDelegate.setDefaultNightMode(newMode)
                    activity?.recreate()
                }
                updateSummaries()
                dialog.dismiss()
            }
            .show()
    }

    private fun showMapTypeDialog() {
        val mapTypes = arrayOf("Normal", "Satellite", "Terrain")
        val currentIndex = PrefManager.mapType - 1

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Map Type")
            .setSingleChoiceItems(mapTypes, currentIndex) { dialog, which ->
                PrefManager.mapType = which + 1
                updateSummaries()
                dialog.dismiss()
            }
            .show()
    }

    private fun showAccuracyDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_accuracy, null)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.accuracy_edit)
        
        editText.setText(PrefManager.accuracy)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_of_accuracy))
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val newValue = editText.text.toString()
                if (newValue.isNotEmpty()) {
                    PrefManager.accuracy = newValue
                    updateSummaries()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRandomPositionDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_random_position, null)
        val switchView = dialogView.findViewById<SwitchMaterial>(R.id.random_position_switch)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.random_position_edit)

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

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Random Position")
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                try {
                    val newValue = editText.text.toString()
                    if (newValue.isNotEmpty()) {
                        PrefManager.randomPositionRange = newValue
                        updateSummaries()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.enter_valid_input), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
} 
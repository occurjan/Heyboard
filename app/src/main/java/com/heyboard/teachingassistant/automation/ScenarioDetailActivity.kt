package com.heyboard.teachingassistant.automation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.heyboard.teachingassistant.R

class ScenarioDetailActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var spinnerTrigger: Spinner
    private lateinit var actionListContainer: LinearLayout

    private var scenario = AutomationScenario()
    private var isNewScenario = true
    private var hasUnsavedChanges = false

    private val triggerValues = arrayOf("on_start", "on_close")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_scenario_detail)

        etName = findViewById(R.id.etScenarioName)
        spinnerTrigger = findViewById(R.id.spinnerTrigger)
        actionListContainer = findViewById(R.id.actionListContainer)

        // Close button
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { handleClose() }

        // Edit name button - focus the EditText
        findViewById<ImageButton>(R.id.btnEditName).setOnClickListener {
            etName.requestFocus()
            etName.setSelection(etName.text.length)
        }

        // Trigger spinner
        val triggerLabels = arrayOf(
            getString(R.string.trigger_on_start),
            getString(R.string.trigger_on_close)
        )
        spinnerTrigger.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, triggerLabels)

        // Load scenario if editing
        val scenarioId = intent.getStringExtra("scenario_id")
        if (scenarioId != null) {
            val loaded = AutomationRepository.getScenario(this, scenarioId)
            if (loaded != null) {
                scenario = loaded
                isNewScenario = false
            }
        }

        populateFields()

        // Add action button
        findViewById<MaterialButton>(R.id.btnAddAction).setOnClickListener {
            // Save current state first
            saveCurrentState()
            AutomationRepository.saveScenario(this, scenario)
            isNewScenario = false

            val intent = Intent(this, ActionDetailActivity::class.java)
            intent.putExtra("scenario_id", scenario.id)
            startActivity(intent)
        }

        // Save button
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveScenario()
        }

        // Delete button
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isNewScenario) {
            val reloaded = AutomationRepository.getScenario(this, scenario.id)
            if (reloaded != null) {
                scenario = reloaded
                refreshActionList()
            }
        }
    }

    private fun populateFields() {
        etName.setText(scenario.name)
        val triggerIndex = triggerValues.indexOf(scenario.trigger)
        if (triggerIndex >= 0) spinnerTrigger.setSelection(triggerIndex)
        refreshActionList()
    }

    private fun refreshActionList() {
        actionListContainer.removeAllViews()
        for ((index, action) in scenario.actions.withIndex()) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_action, actionListContainer, false)
            itemView.findViewById<TextView>(R.id.tvActionIndex).text =
                getString(R.string.action_index, index + 1)
            itemView.findViewById<TextView>(R.id.tvActionName).text =
                action.name.ifEmpty { getString(R.string.serial_command) }
            itemView.findViewById<MaterialButton>(R.id.btnEditAction).setOnClickListener {
                val intent = Intent(this, ActionDetailActivity::class.java)
                intent.putExtra("scenario_id", scenario.id)
                intent.putExtra("action_id", action.id)
                startActivity(intent)
            }
            actionListContainer.addView(itemView)
        }
    }

    private fun saveCurrentState() {
        scenario.name = etName.text.toString().trim()
        scenario.trigger = triggerValues[spinnerTrigger.selectedItemPosition]
    }

    private fun saveScenario() {
        saveCurrentState()
        if (scenario.name.isEmpty()) {
            Toast.makeText(this, R.string.enter_scenario_name, Toast.LENGTH_SHORT).show()
            return
        }
        AutomationRepository.saveScenario(this, scenario)
        isNewScenario = false
        hasUnsavedChanges = false
        Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_scenario)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AutomationRepository.deleteScenario(this, scenario.id)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleClose() {
        val currentName = etName.text.toString().trim()
        if (currentName != scenario.name || hasUnsavedChanges) {
            AlertDialog.Builder(this)
                .setMessage(R.string.unsaved_changes)
                .setPositiveButton(R.string.save) { _, _ ->
                    saveScenario()
                    finish()
                }
                .setNegativeButton(R.string.discard) { _, _ -> finish() }
                .setNeutralButton(R.string.cancel, null)
                .show()
        } else {
            finish()
        }
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        handleClose()
    }
}

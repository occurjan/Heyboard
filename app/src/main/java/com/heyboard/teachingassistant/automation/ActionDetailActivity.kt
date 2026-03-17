package com.heyboard.teachingassistant.automation

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.heyboard.teachingassistant.R

class ActionDetailActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var spinnerActionType: Spinner
    private lateinit var spinnerBaudRate: Spinner
    private lateinit var spinnerDataBits: Spinner
    private lateinit var spinnerStopBits: Spinner
    private lateinit var spinnerParity: Spinner
    private lateinit var spinnerFlowControl: Spinner
    private lateinit var spinnerDataFormat: Spinner
    private lateinit var etSerialData: EditText

    private var scenarioId: String = ""
    private var action = ScenarioAction()
    private var isNewAction = true

    private val baudRates = arrayOf("9600", "19200", "38400", "57600", "115200")
    private val dataBitsArr = arrayOf("5", "6", "7", "8")
    private val stopBitsArr = arrayOf("1", "1.5", "2")
    private val parityValues = arrayOf("None", "Odd", "Even", "Mark", "Space")
    private val flowControlValues = arrayOf("None", "RTS/CTS", "XON/XOFF")
    private val dataFormatValues = arrayOf("HEX", "ASCII")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_action_detail)

        scenarioId = intent.getStringExtra("scenario_id") ?: ""
        val actionId = intent.getStringExtra("action_id")

        etName = findViewById(R.id.etActionName)
        spinnerActionType = findViewById(R.id.spinnerActionType)
        spinnerBaudRate = findViewById(R.id.spinnerBaudRate)
        spinnerDataBits = findViewById(R.id.spinnerDataBits)
        spinnerStopBits = findViewById(R.id.spinnerStopBits)
        spinnerParity = findViewById(R.id.spinnerParity)
        spinnerFlowControl = findViewById(R.id.spinnerFlowControl)
        spinnerDataFormat = findViewById(R.id.spinnerDataFormat)
        etSerialData = findViewById(R.id.etSerialData)

        // Close button
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        // Edit name
        findViewById<ImageButton>(R.id.btnEditName).setOnClickListener {
            etName.requestFocus()
            etName.setSelection(etName.text.length)
        }

        // Setup spinners
        setupSpinner(spinnerActionType, arrayOf(getString(R.string.serial_command)))
        setupSpinner(spinnerBaudRate, baudRates)
        setupSpinner(spinnerDataBits, dataBitsArr)
        setupSpinner(spinnerStopBits, stopBitsArr)

        val parityLabels = arrayOf(
            getString(R.string.parity_none),
            getString(R.string.parity_odd),
            getString(R.string.parity_even),
            getString(R.string.parity_mark),
            getString(R.string.parity_space)
        )
        setupSpinner(spinnerParity, parityLabels)

        val flowLabels = arrayOf(
            getString(R.string.flow_none),
            getString(R.string.flow_rtscts),
            getString(R.string.flow_xonxoff)
        )
        setupSpinner(spinnerFlowControl, flowLabels)
        setupSpinner(spinnerDataFormat, dataFormatValues)

        // Load existing action
        if (actionId != null) {
            val scenario = AutomationRepository.getScenario(this, scenarioId)
            val loaded = scenario?.actions?.find { it.id == actionId }
            if (loaded != null) {
                action = loaded
                isNewAction = false
            }
        }

        populateFields()

        // Save
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveAction() }

        // Test
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { testAction() }

        // Delete
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            if (isNewAction) {
                finish()
            } else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.confirm_delete_action)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        deleteAction()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun setupSpinner(spinner: Spinner, items: Array<String>) {
        spinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun populateFields() {
        etName.setText(action.name)
        val config = action.serialConfig

        selectByValue(spinnerBaudRate, baudRates, config.baudRate.toString())
        selectByValue(spinnerDataBits, dataBitsArr, config.dataBits.toString())
        selectByValue(spinnerStopBits, stopBitsArr, config.stopBits)
        selectByValue(spinnerParity, parityValues, config.parity)
        selectByValue(spinnerFlowControl, flowControlValues, config.flowControl)
        selectByValue(spinnerDataFormat, dataFormatValues, config.dataFormat)
        etSerialData.setText(config.serialData)
    }

    private fun selectByValue(spinner: Spinner, values: Array<String>, value: String) {
        val index = values.indexOf(value)
        if (index >= 0) spinner.setSelection(index)
    }

    private fun readFormToAction() {
        action.name = etName.text.toString().trim()
        action.serialConfig = SerialConfig(
            baudRate = baudRates[spinnerBaudRate.selectedItemPosition].toInt(),
            dataBits = dataBitsArr[spinnerDataBits.selectedItemPosition].toInt(),
            stopBits = stopBitsArr[spinnerStopBits.selectedItemPosition],
            parity = parityValues[spinnerParity.selectedItemPosition],
            flowControl = flowControlValues[spinnerFlowControl.selectedItemPosition],
            serialData = etSerialData.text.toString().trim(),
            dataFormat = dataFormatValues[spinnerDataFormat.selectedItemPosition]
        )
    }

    private fun saveAction() {
        readFormToAction()
        val scenario = AutomationRepository.getScenario(this, scenarioId) ?: return

        if (isNewAction) {
            scenario.actions.add(action)
        } else {
            val index = scenario.actions.indexOfFirst { it.id == action.id }
            if (index >= 0) scenario.actions[index] = action
        }

        AutomationRepository.saveScenario(this, scenario)
        Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun testAction() {
        readFormToAction()
        Log.i("ActionDetail", "Test button clicked, requesting USB permission check first...")

        // First ensure USB permission, then execute
        SerialCommandExecutor.requestUsbPermissionIfNeeded(this) { granted, message ->
            if (!granted) {
                Log.w("ActionDetail", "USB permission not granted: $message")
                showResultDialog(getString(R.string.serial_send_failed) + ": " + message, false)
                return@requestUsbPermissionIfNeeded
            }

            Log.i("ActionDetail", "USB permission OK, executing serial command...")
            Thread {
                try {
                    val result = SerialCommandExecutor.execute(this, action)
                    runOnUiThread {
                        if (result.isSuccess) {
                            showResultDialog(getString(R.string.serial_send_success), true)
                        } else {
                            val errMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                            Log.e("ActionDetail", "Serial execute failed: $errMsg")
                            showResultDialog(getString(R.string.serial_send_failed) + ": " + errMsg, false)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ActionDetail", "Unexpected exception in testAction", e)
                    runOnUiThread {
                        showResultDialog(getString(R.string.serial_send_failed) + ": " + e.message, false)
                    }
                }
            }.start()
        }
    }

    private fun showResultDialog(message: String, success: Boolean) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.confirm, null)
            .show()
    }

    private fun deleteAction() {
        val scenario = AutomationRepository.getScenario(this, scenarioId) ?: return
        scenario.actions.removeAll { it.id == action.id }
        AutomationRepository.saveScenario(this, scenario)
        finish()
    }
}

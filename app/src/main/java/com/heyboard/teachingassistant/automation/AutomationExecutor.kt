package com.heyboard.teachingassistant.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

object AutomationExecutor {

    private const val TAG = "AutomationExecutor"
    private const val PREF_NAME = "automation_executor"
    private const val KEY_LAST_BOOT_TIME = "last_boot_time"

    private val onCloseRunning = AtomicBoolean(false)

    /**
     * Execute on_start scenarios only on the first app launch after device boot.
     * Uses boot timestamp to detect a new boot cycle.
     */
    fun executeOnStart(context: Context) {
        val bootTime = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_MULTI_PROCESS)
        val lastBootTime = prefs.getLong(KEY_LAST_BOOT_TIME, 0L)

        // Allow 5s tolerance for boot time comparison
        if (Math.abs(bootTime - lastBootTime) < 5000) {
            Log.i(TAG, "Not first launch after boot, skipping on_start")
            return
        }

        prefs.edit().putLong(KEY_LAST_BOOT_TIME, bootTime).apply()

        val scenarios = AutomationRepository.getScenariosForTrigger(context, "on_start")
        if (scenarios.isEmpty()) return
        Log.i(TAG, "First launch after boot, executing ${scenarios.size} on_start scenarios")
        Thread {
            // Retry up to 3 times with delay, USB serial driver may not be ready immediately after boot
            for (attempt in 1..3) {
                Log.i(TAG, "on_start attempt $attempt/3")
                var allSuccess = true
                for (scenario in scenarios) {
                    val success = executeScenario(context, scenario)
                    if (!success) allSuccess = false
                }
                if (allSuccess) break
                if (attempt < 3) {
                    Log.i(TAG, "Retrying in 5 seconds...")
                    Thread.sleep(5000)
                }
            }
        }.start()
    }

    /**
     * Execute on_close scenarios. Uses AtomicBoolean to prevent duplicate execution
     * when both FINISH_CLASS and FINISH_CLASS_DONE broadcasts are received.
     */
    fun executeOnClose(context: Context) {
        if (!onCloseRunning.compareAndSet(false, true)) {
            Log.i(TAG, "on_close already running, skipping duplicate trigger")
            return
        }

        try {
            val scenarios = AutomationRepository.getScenariosForTrigger(context, "on_close")
            if (scenarios.isEmpty()) {
                Log.i(TAG, "No on_close scenarios configured")
                return
            }
            Log.i(TAG, "Executing ${scenarios.size} on_close scenarios")
            for (scenario in scenarios) {
                executeScenario(context, scenario)
            }
        } finally {
            onCloseRunning.set(false)
        }
    }

    /**
     * Execute a single action directly (for test button in ActionDetailActivity).
     */
    fun executeSingleAction(context: Context, action: ScenarioAction): Result<Unit> {
        return SerialCommandExecutor.execute(context, action)
    }

    private fun executeScenario(context: Context, scenario: AutomationScenario): Boolean {
        Log.i(TAG, "Executing scenario: ${scenario.name}")
        var allSuccess = true
        for (action in scenario.actions) {
            val result = SerialCommandExecutor.execute(context, action)
            if (result.isFailure) {
                Log.e(TAG, "Action '${action.name}' failed: ${result.exceptionOrNull()?.message}")
                allSuccess = false
            }
        }
        return allSuccess
    }
}
